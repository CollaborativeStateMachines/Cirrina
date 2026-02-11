package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.Runtime
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.spec.StateMachine as StateMachineSpec
import at.ac.uibk.dps.cirrina.spec.Transition as TransitionSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.get
import kotlin.properties.Delegates
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val VAR_PREFIX = "$"

class StateMachine
@AssistedInject
internal constructor(
  @Assisted val name: String,
  @Assisted val specification: StateMachineSpec,
  @Assisted private val runtime: Runtime,
  @Assisted private val selector: ServiceImplementationSelector,
  @Assisted private val parent: StateMachine? = null,
  @Assisted private val subscriptions: List<String>? = null,
  @Assisted private val data: List<ContextVariable>? = null,
  private val observationRegistry: ObservationRegistry,
  private val commandFactory: ActionCommandFactory,
  private val stateFactory: State.Factory,
  private val transitionFactory: Transition.Factory,
  eventHandler: EventHandler,
) : Scope {
  var nested: List<String> by
    Delegates.vetoable(emptyList()) { _, old, _ ->
      if (old.isNotEmpty()) error("nested instance names is already set")
      true
    }

  private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private val timeoutActionManager = TimeoutActionManager(coroutineScope)
  private val stateMachineEventHandler = StateMachineEventHandler(eventHandler)
  private val stateInstances =
    specification.vertexSet().associate { it.name to stateFactory.create(it, this) }

  private val started = CompletableDeferred<Unit>()
  private val eventChannel = Channel<Event>(Channel.UNLIMITED)
  private var activeState: State? = null

  override val extent: Extent
  private val instanceObservation =
    Observation.start("stateMachine.instance", observationRegistry).apply {
      lowCardinalityKeyValue("stateMachine.instanceName", name)
    }

  init {
    val transientContext = Context.from(specification.transientContext)
    data?.forEach { transientContext.create(it.name, it.value) }

    extent = (parent?.extent ?: runtime.extent).extend(transientContext)

    subscriptions?.forEach { stateMachineEventHandler.eventHandler.subscribe(it) }

    runtime.phaser.register()
  }

  fun start() {
    logger.info { "'$this' entering initial state" }

    instanceObservation.observe {
      val initial = stateInstances[specification.initial.name] ?: error("initial state not found")
      doEnter(initial, null)?.let { step(it, null) }

      started.complete(Unit)
    }

    logger.info { "'$this' starting event processor" }
    processEvents()
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

      runtime.phaser.arriveAndDeregister()
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
        handleEvent(event)?.let { transition -> step(transition, event) }

        if (event.channel == EventChannel.INTERNAL)
          stateMachineEventHandler.propagateToNested(event)
      }
  }

  private fun handleEvent(event: Event): Transition? {
    val current = activeState ?: error("received event '$event' before entering initial state")
    val candidates =
      specification.getOnTransitionsFromStateByEventName(current.specification, event.topic)
    if (candidates.isEmpty()) return null

    val evalExtent =
      extent.extend(
        ContextInMemory().apply { event.data.forEach { create(VAR_PREFIX + it.name, it.value) } }
      )

    return trySelect(candidates, evalExtent)?.also {
      event.data.forEach { d -> extent.setOrCreate(VAR_PREFIX + d.name, d.value) }
    }
  }

  private fun Event.isValid(): Boolean {
    if (target.isNotEmpty() && target != name) return false
    if (channel != EventChannel.INTERNAL && source.isEmpty()) return false
    if (channel == EventChannel.EXTERNAL && subscriptions?.contains(source) == false) return false

    return true
  }

  private fun step(transition: Transition, event: Event?) {
    if (transition.isInternal) {
      doTransition(transition, event)
      return
    }

    val target =
      stateInstances[transition.targetStateName]
        ?: error("target state '${transition.targetStateName}' not found")
    val current = activeState ?: error("no active state to transition from")

    doExit(current, event)
    doTransition(transition, event)

    doEnter(target, event)?.let { step(it, event) }
  }

  private fun trySelect(transitions: List<TransitionSpec>, evalExtent: Extent): Transition? {
    val selected =
      transitions.mapNotNull { tc ->
        val result = tc.evaluate(evalExtent)
        when {
          result -> transitionFactory.create(tc, isOr = false)
          tc.or != null -> transitionFactory.create(tc, isOr = true)
          else -> null
        }
      }
    return when (selected.size) {
      0 -> null
      1 -> selected.first()
      else -> error("non-determinism detected with selected transitions '$selected'")
    }
  }

  private fun doEnter(state: State, event: Event?): Transition? =
    Observation.createNotStarted("stateMachine.enter", observationRegistry)
      .lowCardinalityKeyValue("stateMachine.instanceName", name)
      .lowCardinalityKeyValue("state.name", state.specification.name)
      .observe<Transition?> {
        activeState = state

        execute(state.getEntryActionCommands(createContext(state, event)))
        execute(state.getWhileActionCommands(createContext(state, event, isWhile = true)))

        state.timeout.forEach(::startTimeout)

        handleTermination()

        if (!isTerminated()) {
          trySelect(specification.getAlwaysTransitionsFromState(state.specification), extent)
        } else null
      }

  private fun doExit(state: State, event: Event?) =
    Observation.createNotStarted("stateMachine.exit", observationRegistry)
      .lowCardinalityKeyValue("state.name", state.specification.name)
      .observe {
        timeoutActionManager.stopAll()

        execute(state.getExitActionCommands(createContext(state, event)))
      }

  private fun doTransition(transition: Transition, event: Event?) =
    Observation.createNotStarted("stateMachine.transition", observationRegistry).observe {
      if (!transition.isOr) {
        execute(transition.getActionCommands(createContext(this, event)))
      }
    }

  private tailrec fun execute(commands: List<ActionCommand>) {
    if (commands.isEmpty()) return
    val next =
      commands.flatMap { command ->
        command.execute().also {
          if (command is TimeoutResetActionCommand) stopTimeout(command.timeoutResetAction.action)
        }
      }
    execute(next)
  }

  private fun createContext(scope: Scope, event: Event?, isWhile: Boolean = false) =
    CommandExecutionContext(
      scope,
      selector,
      stateMachineEventHandler,
      coroutineScope,
      event,
      isWhile,
    )

  private fun startTimeout(timeout: TimeoutAction) {
    val delay =
      timeout.delay.execute(extent) as? Number
        ?: error("timeout delay '${timeout.delay}' is non-numeric")

    timeoutActionManager.start(timeout.name, delay) {
      val action = timeout.`do`
      val command = commandFactory.create(action, createContext(this, null))

      execute(
        listOf(
          command as? RaiseActionCommand ?: error("timeout action '$action' must be a raise action")
        )
      )
    }
  }

  private fun stopTimeout(name: String) = timeoutActionManager.stop(name)

  override fun toString() = "StateMachine(name='$name')"

  inner class StateMachineEventHandler(val eventHandler: EventHandler) {
    fun sendEvent(event: Event) = eventHandler.send(event.copy(source = name))

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
      runtime: Runtime,
      selector: ServiceImplementationSelector,
      parent: StateMachine?,
      subscriptions: List<String>?,
      data: List<ContextVariable>?,
    ): StateMachine
  }
}

class TimeoutActionManager(private val coroutineScope: CoroutineScope) {

  private val timeoutJobs = ConcurrentHashMap<String, Job>()

  fun start(actionName: String, delayInMs: Number, task: suspend () -> Unit) {
    check(coroutineScope.isActive) { "cannot start action '$actionName' on a shut down manager" }

    require(!timeoutJobs.containsKey(actionName)) { "duplicate timeout action name '$actionName'" }

    coroutineScope
      .launch {
        while (isActive) {
          delay(delayInMs.toLong())

          runCatching { task() }
            .onFailure { e -> logger.error(e) { "timeout action '$actionName' failed" } }
        }
      }
      .also { timeoutJobs[actionName] = it }
  }

  fun stop(actionName: String) {
    timeoutJobs.remove(actionName)?.cancel()
      ?: error("expected exactly one timeout action with the name '$actionName'")
  }

  fun stopAll() {
    timeoutJobs.values.forEach { it.cancel() }
    timeoutJobs.clear()
  }

  fun shutdown() {
    stopAll()
    coroutineScope.cancel()
  }
}
