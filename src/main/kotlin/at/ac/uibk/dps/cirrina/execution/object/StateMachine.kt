package at.ac.uibk.dps.cirrina.execution.`object`

import TimeoutActionManager
import at.ac.uibk.dps.cirrina.Runtime
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.StateMachine.Factory
import at.ac.uibk.dps.cirrina.spec.Instance
import at.ac.uibk.dps.cirrina.spec.StateMachine as StateMachineSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import kotlin.properties.Delegates
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val VAR_PREFIX = "$"

private data class ActiveTransition(val transition: Transition, val isOr: Boolean)

class StateMachine
@AssistedInject
internal constructor(
  @Assisted val name: String,
  @Assisted val specification: StateMachineSpec,
  @Assisted val instance: Instance,
  @Assisted val subscriptions: List<String>,
  @Assisted private val runtime: Runtime,
  @Assisted private val parent: StateMachine? = null,
  private val observationRegistry: ObservationRegistry,
  private val stateFactory: State.Factory,
  private val transitionFactory: Transition.Factory,
) : Scope {
  var nested: List<String> by
    Delegates.vetoable(emptyList()) { _, old, _ ->
      if (old.isNotEmpty()) error("nested instance names is already set")
      true
    }

  private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private val timeoutActionManager = TimeoutActionManager(coroutineScope)
  private val stateMachineEventHandler = StateMachineEventHandler(runtime.eventHandler)
  private val actionExecutor =
    ActionExecutor(
      runtime.selector,
      stateMachineEventHandler,
      coroutineScope,
      runtime.meterRegistry,
    )

  private val stateInstances =
    specification.vertexSet().associate { it.name to stateFactory.create(it, this) }

  private val onTransitions: Map<String, Map<String, List<Transition>>> =
    specification.vertexSet().associate { state ->
      state.name to
        specification
          .outgoingEdgesOf(state)
          .filter { it.event != null }
          .groupBy { it.event!! }
          .mapValues { (_, specs) -> specs.map { transitionFactory.create(it) } }
    }

  private val alwaysTransitions: Map<String, List<Transition>> =
    specification.vertexSet().associate { state ->
      state.name to
        specification
          .outgoingEdgesOf(state)
          .filter { it.event == null }
          .map { transitionFactory.create(it) }
    }

  private val started = CompletableDeferred<Unit>()
  private val eventChannel = Channel<Event>(Channel.UNLIMITED)
  private var activeState: State? = null

  override val extent: Extent

  private val instanceObservation =
    Observation.start("stateMachine.instance", observationRegistry).apply {
      lowCardinalityKeyValue("stateMachine.instanceName", name)
    }

  init {
    val transientContext = Context.from(specification.transient)
    val instanceData = Context.from(instance.data).getAll()

    instanceData.forEach { transientContext.create(it.name, it.value) }

    extent = (parent?.extent ?: runtime.extent).extend(transientContext)
  }

  fun start(): Job {
    logger.info { "'$this' entering initial state" }

    instanceObservation.observe {
      val initial = stateInstances[specification.initial.name] ?: error("initial state not found")
      doEnter(initial)?.let { step(it) }

      started.complete(Unit)
    }

    logger.info { "'$this' starting event processor" }
    return processEvents()
  }

  private fun isTerminated(): Boolean =
    parent?.isTerminated() == true || activeState?.specification?.terminal == true

  private fun handleTermination() {
    if (isTerminated()) {
      logger.info { "'$this' terminated" }

      instanceObservation.stop()
      timeoutActionManager.shutdown()

      eventChannel.close()
      coroutineScope.cancel()
    }
  }

  fun pushEvent(event: Event) {
    if (event.isValid() && !isTerminated())
      eventChannel.trySend(event).onFailure { logger.error(it) { "failed to push event" } }
  }

  private fun processEvents() =
    coroutineScope.launch {
      started.await()

      for (event in eventChannel) try {
        processEvent(event)
      } catch (e: Exception) {
        logger.error(e) { "failed to process event" }
      }
    }

  private fun processEvent(event: Event) {
    if (isTerminated()) return

    Observation.createNotStarted("stateMachine.event", observationRegistry)
      .lowCardinalityKeyValue("stateMachine.instanceName", name)
      .lowCardinalityKeyValue("event.topic", event.topic)
      .parentObservation(instanceObservation)
      .observe {
        handleEvent(event)?.let { transition -> step(transition) }

        if (event.channel == EventChannel.INTERNAL)
          stateMachineEventHandler.propagateToNested(event)
      }
  }

  private fun handleEvent(event: Event): ActiveTransition? {
    val current = activeState ?: error("received event '$event' before entering initial state")
    val candidates = onTransitions[current.specification.name]?.get(event.topic).orEmpty()

    if (candidates.isEmpty()) return null

    val evalExtent =
      activeState!!.extent.with(event.data.associate { VAR_PREFIX + it.name to it.value })

    return trySelect(candidates, evalExtent)?.also {
      event.data.forEach { d -> extent.setOrCreate(VAR_PREFIX + d.name, d.value) }
    }
  }

  private fun Event.isValid(): Boolean {
    if (target.isNotEmpty() && target != name) return false
    if (channel == EventChannel.EXTERNAL && source !in subscriptions) return false

    return true
  }

  private fun step(initialTransition: ActiveTransition) {
    var currentActive: ActiveTransition? = initialTransition

    while (currentActive != null) {
      val transition = currentActive.transition
      val isOr = currentActive.isOr

      if (transition.isInternal) {
        doTransition(transition, isOr)
        return
      }

      val targetName = transition.targetStateName(isOr) ?: error("target state string is null")
      val target = stateInstances[targetName] ?: error("target state '$targetName' not found")
      val current = activeState ?: error("no active state to transition from")

      doExit(current)
      doTransition(transition, isOr)

      currentActive = doEnter(target)
    }
  }

  private fun trySelect(transitions: List<Transition>, evalExtent: Extent): ActiveTransition? {
    val selected =
      transitions.mapNotNull { transition ->
        val spec = transition.specification
        when {
          spec.evaluate(evalExtent) -> ActiveTransition(transition, isOr = false)
          spec.or != null -> ActiveTransition(transition, isOr = true)
          else -> null
        }
      }

    return when (selected.size) {
      0 -> null
      1 -> selected.first()
      else -> error("non-determinism detected with selected transitions '$selected'")
    }
  }

  private fun doEnter(state: State): ActiveTransition? =
    Observation.createNotStarted("stateMachine.enter", observationRegistry)
      .lowCardinalityKeyValue("stateMachine.instanceName", name)
      .lowCardinalityKeyValue("state.name", state.specification.name)
      .observe<ActiveTransition?> {
        activeState = state

        execute(state.entryActions, state)
        execute(state.duringActions, state)

        state.timeout.forEach(::startTimeout)

        handleTermination()

        if (!isTerminated()) {
          trySelect(alwaysTransitions[state.specification.name].orEmpty(), extent)
        } else null
      }

  private fun doExit(state: State) =
    Observation.createNotStarted("stateMachine.exit", observationRegistry)
      .lowCardinalityKeyValue("state.name", state.specification.name)
      .observe {
        timeoutActionManager.stopAll()

        execute(state.exitActions, state)
      }

  private fun doTransition(transition: Transition, isOr: Boolean) =
    Observation.createNotStarted("stateMachine.transition", observationRegistry).observe {
      if (!isOr) {
        execute(transition.actions, activeState!!)
      }
    }

  private tailrec fun execute(actions: List<Action>, scope: Scope) {
    if (actions.isEmpty()) return

    val next =
      actions.flatMap { action ->
        if (action is TimeoutResetAction) stopTimeout(action.action)
        actionExecutor.execute(action, scope)
      }

    execute(next, scope)
  }

  private fun startTimeout(timeout: TimeoutAction) {
    val delay =
      timeout.delay.execute(extent) as? Number
        ?: error("timeout delay '${timeout.delay}' is non-numeric")

    timeoutActionManager.start(timeout.name, delay) {
      val action = timeout.triggers
      if (action !is EmitAction) error("timeout action '$action' must be an emit action")

      execute(listOf(action), this)
    }
  }

  private fun stopTimeout(name: String) = timeoutActionManager.stop(name)

  override fun toString() = "StateMachine(name='$name')"

  inner class StateMachineEventHandler(val eventHandler: EventHandler) {
    fun emit(event: Event) = eventHandler.emit(event.copy(source = name))

    fun propagateToParent(event: Event) {
      parent?.stateMachineEventHandler?.propagateToParent(event) ?: pushEvent(event)
    }

    fun propagateToNested(event: Event) {
      nested.forEach { name ->
        runtime.findStateMachineInstance(name)?.pushEvent(event)
          ?: error("nested state machine instance '$name' missing")
      }
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(
      name: String,
      specification: StateMachineSpec,
      instance: Instance,
      subscriptions: List<String>,
      runtime: Runtime,
      parent: StateMachine?,
    ): StateMachine
  }
}

fun Factory.createHierarchy(
  name: String,
  specification: StateMachineSpec,
  instance: Instance,
  subscriptions: List<String>,
  runtime: Runtime,
  parent: StateMachine?,
): List<StateMachine> =
  create(name, specification, instance, subscriptions, runtime, parent).let { current ->
    specification.nested
      .flatMapIndexed { index, nested ->
        createHierarchy(
          "${current.name}.$index@${nested.name}",
          nested,
          instance,
          subscriptions,
          runtime,
          current,
        )
      }
      .let { nestedInstances ->
        current.apply { nested = nestedInstances.map { it.name } }
        listOf(current) + nestedInstances
      }
  }
