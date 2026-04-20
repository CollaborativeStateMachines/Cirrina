package at.ac.uibk.dps.cirrina.execution.`object`

import TimeoutActionManager
import at.ac.uibk.dps.cirrina.Runtime
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.StateMachine.Factory
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.spec.Action
import at.ac.uibk.dps.cirrina.spec.ContextVariable
import at.ac.uibk.dps.cirrina.spec.Emit
import at.ac.uibk.dps.cirrina.spec.Event
import at.ac.uibk.dps.cirrina.spec.Reset
import at.ac.uibk.dps.cirrina.spec.StateMachine as StateMachineSpecification
import at.ac.uibk.dps.cirrina.spec.Timeout
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates
import kotlin.time.Clock
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val VAR_PREFIX = "$"

@JvmInline
value class ActiveTransition(private val wrapped: Any) {
  val transition: Transition
    get() = if (wrapped is OrMarker) wrapped.t else wrapped as Transition

  val isOr: Boolean
    get() = wrapped is OrMarker

  private class OrMarker(val t: Transition)

  companion object {
    fun standard(t: Transition) = ActiveTransition(t)

    fun or(t: Transition) = ActiveTransition(OrMarker(t))
  }
}

class StateMachine
@AssistedInject
internal constructor(
  @Assisted val name: String,
  @Assisted val specification: StateMachineSpecification,
  @Assisted val subscription: Regex,
  @Assisted override val instanceRegistry: Runtime.InstanceRegistry,
  @Assisted private val parent: StateMachine?,
  @Assisted data: List<ContextVariable>,
  @Assisted runtimeExtent: Extent,
  @Assisted eventHandler: EventHandler,
  @Assisted serviceImplementationSelector: ServiceImplementationSelector,
  stateFactory: State.Factory,
  transitionFactory: Transition.Factory,
  metricRegistry: MetricRegistry,
) : Scope {
  override val extent: Extent

  var outputEvents = specification.outputEvents.map { it.copy(source = name) }

  var nested: List<String> by
    Delegates.vetoable(emptyList()) { _, old, _ ->
      if (old.isNotEmpty()) error("nested instance names is already set")
      true
    }

  private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  private val timeoutActionManager = TimeoutActionManager(coroutineScope)

  private val stateMachineEventHandler = StateMachineEventHandler(eventHandler)

  private val actionExecutor =
    ActionExecutor(
      serviceImplementationSelector,
      metricRegistry,
      stateMachineEventHandler,
      coroutineScope,
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

  private val eventExtent: Extent

  private val eventTimer: Timer = metricRegistry.timer("event.latency")

  private val processEventTimer: Timer = metricRegistry.timer("processEvent.time")

  init {
    val transientContext =
      Context.empty().apply {
        specification.transient.forEach { (k, v) -> create(k, v.evaluate()) }
        data.forEach { create(it.name, it.value) }
      }

    val eventContext = Context.empty()

    extent = (parent?.extent ?: runtimeExtent).extend(transientContext)
    eventExtent = extent.extend(eventContext)
  }

  fun start(parentContext: Job): Job {
    logger.info { "'$this' entering initial state" }

    val initial = stateInstances[specification.initial.name] ?: error("initial state not found")
    doEnter(initial)?.let { step(it) }

    started.complete(Unit)

    logger.info { "'$this' starting event processor" }
    return processEvents(parentContext)
  }

  private fun isTerminated(): Boolean =
    parent?.isTerminated() == true || activeState?.specification?.terminal == true

  private fun handleTermination() {
    if (isTerminated()) {
      logger.info { "'$this' terminated" }

      timeoutActionManager.shutdown()

      eventChannel.close()
      coroutineScope.cancel()
    }
  }

  fun pushEvent(event: Event) {
    if (event.isValid() && !isTerminated())
      eventChannel.trySend(event).onFailure { logger.error(it) { "failed to push event" } }
  }

  private fun processEvents(parentContext: Job) =
    coroutineScope.launch(parentContext) {
      started.await()

      for (event in eventChannel) try {
        processEvent(event)
      } catch (e: Exception) {
        logger.error(e) { "failed to process event" }
      }
    }

  private fun processEvent(event: Event) {
    val delta = measureTime {
      if (isTerminated()) return

      if (
        (event.channel == EventChannel.EXTERNAL || event.channel == EventChannel.PERIPHERAL) &&
          event.source != name
      ) {
        val now = Clock.System.now()
        val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
        val deltaNanos = (nowNanos - event.emittedTime).coerceAtLeast(0L)

        eventTimer.update(deltaNanos, TimeUnit.NANOSECONDS)
      }

      handleEvent(event)?.let { transition -> step(transition) }

      if (event.channel == EventChannel.INTERNAL) stateMachineEventHandler.propagateToNested(event)
    }

    processEventTimer.update(delta.toJavaDuration())
  }

  private fun handleEvent(event: Event): ActiveTransition? {
    val current = activeState ?: error("received event '$event' before entering initial state")
    val candidates = onTransitions[current.specification.name]?.get(event.topic).orEmpty()

    if (candidates.isEmpty()) return null

    val eventContext = eventExtent.high!!

    event.data.forEach { eventContext.create(VAR_PREFIX + it.name, it.value) }

    val selected =
      trySelect(candidates, eventExtent)?.also {
        event.data.forEach { d -> extent.setOrCreate(VAR_PREFIX + d.name, d.value) }
      }

    eventContext.clear()

    return selected
  }

  private fun Event.isValid(): Boolean {
    if (target.isNotEmpty() && target != name) return false
    if (channel == EventChannel.EXTERNAL && !subscription.matches(source)) return false

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
    for (i in transitions.indices) {
      val transition = transitions[i]
      val spec = transition.specification

      if (spec.provided?.evaluatesToTrue(evalExtent) ?: true) {
        return ActiveTransition.standard(transition)
      }

      if (spec.or != null) return ActiveTransition.or(transition)
    }
    return null
  }

  private fun doEnter(state: State): ActiveTransition? {
    activeState = state

    execute(state.entry, state)
    execute(state.during, state)

    state.timeout.forEach(::startTimeout)

    handleTermination()

    return if (!isTerminated()) {
      trySelect(alwaysTransitions[state.specification.name].orEmpty(), extent)
    } else null
  }

  private fun doExit(state: State) {
    timeoutActionManager.stopAll()
    execute(state.exit, state)
  }

  private fun doTransition(transition: Transition, isOr: Boolean) {
    if (!isOr) {
      execute(transition.yields, activeState!!)
    }
  }

  private tailrec fun execute(actions: List<Action>, scope: Scope) {
    if (actions.isEmpty()) return

    val nextActions = mutableListOf<Action>()

    for (i in actions.indices) {
      val action = actions[i]

      if (action is Reset) {
        stopTimeout(action.action)
      }

      val result = actionExecutor.execute(action, scope)
      if (result.isNotEmpty()) {
        nextActions.addAll(result)
      }
    }

    execute(nextActions, scope)
  }

  private fun startTimeout(timeout: Timeout) {
    val delay =
      timeout.delay.evaluate(extent) as? Number
        ?: error("timeout delay '${timeout.delay}' is non-numeric")

    timeoutActionManager.start(timeout.name, delay) {
      val action = timeout.triggers
      if (action !is Emit) error("timeout action '$action' must be an emit action")

      execute(listOf(action), this)
    }
  }

  private fun stopTimeout(name: String) = timeoutActionManager.stop(name)

  override fun toString() = "StateMachine(name='$name')"

  inner class StateMachineEventHandler(val eventHandler: EventHandler) {
    fun emit(event: Event) {
      val now = Clock.System.now()
      val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond

      eventHandler.emit(event.copy(source = name, emittedTime = epochNanos))
    }

    fun propagateToParent(event: Event) {
      parent?.stateMachineEventHandler?.propagateToParent(event) ?: pushEvent(event)
    }

    fun propagateToNested(event: Event) {
      nested.forEach { name ->
        instanceRegistry.findStateMachineInstance(name)?.pushEvent(event)
          ?: error("nested state machine instance '$name' missing")
      }
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(
      name: String,
      specification: StateMachineSpecification,
      subscription: Regex,
      registry: Runtime.InstanceRegistry,
      data: List<ContextVariable>,
      parent: StateMachine?,
      runtimeExtent: Extent,
      eventHandler: EventHandler,
      serviceImplementationSelector: ServiceImplementationSelector,
    ): StateMachine
  }
}

fun Factory.createHierarchy(
  name: String,
  specification: StateMachineSpecification,
  subscription: Regex,
  instanceRegistry: Runtime.InstanceRegistry,
  data: List<ContextVariable>,
  parent: StateMachine?,
  runtimeExtent: Extent,
  eventHandler: EventHandler,
  serviceImplementationSelector: ServiceImplementationSelector,
): List<StateMachine> =
  create(
      name,
      specification,
      subscription,
      instanceRegistry,
      data,
      parent,
      runtimeExtent,
      eventHandler,
      serviceImplementationSelector,
    )
    .let { current ->
      specification.nested
        .flatMap { nested ->
          createHierarchy(
            "${current.name}@${nested.name}",
            nested,
            subscription,
            instanceRegistry,
            data,
            current,
            runtimeExtent,
            eventHandler,
            serviceImplementationSelector,
          )
        }
        .let { nestedInstances ->
          current.apply { nested = nestedInstances.map { it.name } }
          listOf(current) + nestedInstances
        }
    }
