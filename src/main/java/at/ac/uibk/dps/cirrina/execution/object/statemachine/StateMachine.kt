package at.ac.uibk.dps.cirrina.execution.`object`.statemachine

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.command.*
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.*
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.state.State
import at.ac.uibk.dps.cirrina.execution.`object`.transition.Transition
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.spec.StateMachine as StateMachineSpec
import at.ac.uibk.dps.cirrina.spec.Transition as TransitionSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val VAR_PREFIX = "$"

class StateMachine
@AssistedInject
constructor(
  @Assisted val instanceName: String,
  @Assisted private val runtime: Runtime,
  @Assisted private val stateMachineSpec: StateMachineSpec,
  @Assisted private val parentStateMachine: StateMachine? = null,
  @Assisted private val eventSubscriptions: List<String>? = null,
  @Assisted private val data: List<ContextVariable>? = null,
  private val meterRegistry: MeterRegistry,
  private val observationRegistry: ObservationRegistry,
  private val serviceImplementationSelector: ServiceImplementationSelector,
  eventHandler: EventHandler,
) : EventListener, Scope {

  @AssistedFactory
  interface Factory {
    fun create(
      instanceName: String,
      runtime: Runtime,
      stateMachineSpec: StateMachineSpec,
      parentStateMachine: StateMachine?,
      eventSubscriptions: List<String>?,
      data: List<ContextVariable>?,
    ): StateMachine
  }

  var nestedStateMachineInstanceNames: List<String> by
    Delegates.vetoable(emptyList()) { _, old, _ ->
      if (old.isNotEmpty()) error("nestedStateMachineInstanceNames is already set")
      true
    }

  private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private val timeoutActionManager = TimeoutActionManager(coroutineScope)
  private val stateMachineEventHandler = StateMachineEventHandler(eventHandler)
  private val stateInstances = stateMachineSpec.vertexSet().associate { it.name to State(it, this) }

  private val eventQueue = ConcurrentLinkedQueue<Event>()
  private val isProcessing = AtomicBoolean(false)
  private var activeState: State? = null

  override val extent: Extent

  private val instanceObservation =
    Observation.start("stateMachine.instance", observationRegistry).apply {
      lowCardinalityKeyValue("stateMachine.instanceName", instanceName)
    }

  init {
    val transientContext = buildTransientContext()

    data?.forEach { transientContext.create(it.name, it.value) }

    extent =
      parentStateMachine?.extent?.extend(transientContext)
        ?: runtime.extent.extend(transientContext)

    eventSubscriptions?.forEach { stateMachineEventHandler.eventHandler.subscribe(it) }
    runtime.phaser.register()
  }

  fun start() {
    instanceObservation.observe {
      val initial =
        stateInstances[stateMachineSpec.initialState.name] ?: error("initial state not found")

      doEnter(initial, null)?.let { step(it, null) }
      processQueue()
    }
  }

  override fun onReceiveEvent(event: Event) {
    event
      .takeIf { it.isValid() }
      ?.let {
        eventQueue.add(it)
        processQueue()
      }
  }

  private fun processQueue() {
    if (isProcessing.compareAndSet(false, true)) {
      try {
        while (true) {
          val event = eventQueue.poll() ?: break
          processEvent(event)
        }
      } finally {
        isProcessing.set(false)
        // Final check to ensure no events were added during the lock release
        if (eventQueue.isNotEmpty()) processQueue()
      }
    }
  }

  private fun processEvent(event: Event) {
    if (isTerminated()) return

    Observation.createNotStarted("stateMachine.event", observationRegistry)
      .lowCardinalityKeyValue("stateMachine.instanceName", instanceName)
      .lowCardinalityKeyValue("event.topic", event.topic)
      .parentObservation(instanceObservation)
      .observe {
        // Attempt local state transition
        handleEvent(event)?.let { transition -> step(transition, event) }

        // If the event is internal, tunnel it down to nested instances
        if (event.channel == EventChannel.INTERNAL) {
          stateMachineEventHandler.propagateToNested(event)
        }
      }
  }

  private fun Event.isValid(): Boolean {
    // If a target is specified, it must match this instance name
    if (!target.isEmpty() && target != instanceName) {
      return false
    }
    // External events must have a source
    if (channel != EventChannel.INTERNAL && source.isEmpty()) {
      logger.warn { "$this received event '$topic' with unspecified source" }
      return false
    }
    // External events must match active subscriptions
    if (channel == EventChannel.EXTERNAL && eventSubscriptions?.contains(source) == false) {
      return false
    }
    return true
  }

  private tailrec fun step(transition: Transition, event: Event?) {
    if (transition.isInternal) {
      doTransition(transition, event)
      return
    }

    val target = stateInstances[transition.targetStateName] ?: error("target not found")
    val current = activeState ?: error("no active state to transition from")

    doExit(current, event)
    doTransition(transition, event)

    val next = doEnter(target, event)
    if (next != null) step(next, event)
  }

  private fun handleEvent(event: Event): Transition? {
    val current = activeState ?: return null
    val candidates =
      stateMachineSpec.getOnTransitionsFromStateByEventName(current.state, event.topic)
    if (candidates.isEmpty()) return null

    val evalExtent =
      extent.extend(
        event.data.fold(InMemoryContext()) { context, it ->
          context.apply { create(VAR_PREFIX + it.name, it.value) }
        }
      )

    return trySelect(candidates, evalExtent)?.also {
      event.data.forEach { d -> extent.setOrCreate(VAR_PREFIX + d.name, d.value) }
    }
  }

  private fun doEnter(state: State, event: Event?): Transition? {
    return Observation.createNotStarted("stateMachine.enter", observationRegistry)
      .lowCardinalityKeyValue("stateMachine.instanceName", instanceName)
      .lowCardinalityKeyValue("state.name", state.state.name)
      .lowCardinalityKeyValue("event.topic", event?.topic ?: "none")
      .observe<Transition?> {
        // Change the active state
        activeState = state

        val factory = createFactory(state, event)

        // Execute entry and while actions
        execute(state.getEntryActionCommands(factory))
        execute(state.getWhileActionCommands(createFactory(state, event, true)))

        // Start timeout actions
        state.getTimeoutActionObjects().forEach(::startTimeout)

        // Check termination
        checkTermination()

        // Proceed if not terminated
        if (!isTerminated()) {
          trySelect(stateMachineSpec.getAlwaysTransitionsFromState(state.state), extent)
        } else null
      }
  }

  private fun doExit(state: State, event: Event?) {
    return Observation.createNotStarted("stateMachine.exit", observationRegistry)
      .lowCardinalityKeyValue("stateMachine.instanceName", instanceName)
      .lowCardinalityKeyValue("state.name", state.state.name)
      .lowCardinalityKeyValue("event.topic", event?.topic ?: "none")
      .observe {
        // Stop all timeout actions
        timeoutActionManager.stopAll()

        // Execute exit actions
        execute(state.getExitActionCommands(createFactory(state, event)))
      }
  }

  private fun doTransition(transition: Transition, event: Event?) {
    return Observation.createNotStarted("stateMachine.transition", observationRegistry)
      .lowCardinalityKeyValue("stateMachine.instanceName", instanceName)
      .lowCardinalityKeyValue("event.topic", event?.topic ?: "none")
      .observe {
        // Execute transition actions, if the transition is an or we skip the transitions
        if (!transition.isOr) {
          execute(transition.getActionCommands(createFactory(this, event)))
        }
      }
  }

  private fun trySelect(transitions: List<TransitionSpec>, evalExtent: Extent): Transition? {
    val selected =
      transitions.mapNotNull { tc ->
        val result = tc.evaluate(evalExtent)
        when {
          result -> Transition(tc, isOr = false)
          tc.or != null -> Transition(tc, isOr = true)
          else -> null
        }
      }

    return when (selected.size) {
      0 -> null
      1 -> selected.first()
      else -> error("non-determinism detected in $this")
    }
  }

  private tailrec fun execute(commands: List<ActionCommand>) {
    if (commands.isEmpty()) return
    val nextBatch =
      commands.flatMap { command ->
        command.execute().also {
          if (command is ActionTimeoutResetCommand) stopTimeout(command.timeoutResetAction.action)
        }
      }
    execute(nextBatch)
  }

  private fun startTimeout(timeout: TimeoutAction) {
    val delay = timeout.delay.execute(extent) as? Number ?: error("non-numeric delay")
    timeoutActionManager.start(timeout.name, delay) {
      val command = createFactory(this, null).createActionCommand(timeout.`do`)
      execute(listOf(command as? ActionRaiseCommand ?: error("must be raise")))
    }
  }

  private fun stopTimeout(name: String) = timeoutActionManager.stop(name)

  private fun isTerminated(): Boolean =
    parentStateMachine?.isTerminated() == true || activeState?.state?.terminal == true

  private fun checkTermination() {
    if (isTerminated()) {
      logger.info { "state machine '$instanceName' terminated" }

      instanceObservation.stop()
      timeoutActionManager.shutdown()
      coroutineScope.cancel()
      runtime.phaser.arriveAndDeregister()
    }
  }

  private fun buildTransientContext(): Context =
    stateMachineSpec.transientContextDescription?.let {
      ContextBuilder.from(it).inMemoryContext().build().getOrThrow()
    } ?: ContextBuilder.empty().inMemoryContext().build().getOrThrow()

  private fun createFactory(scope: Scope, event: Event?, isWhile: Boolean = false) =
    CommandFactory(
      ExecutionContext(
        scope,
        serviceImplementationSelector,
        stateMachineEventHandler,
        coroutineScope,
        event,
        isWhile,
      ),
      meterRegistry,
    )

  override fun toString() = "StateMachine(name='$instanceName')"

  inner class StateMachineEventHandler(val eventHandler: EventHandler) {
    fun sendEvent(event: Event) = eventHandler.send(event.withSource(instanceName))

    fun propagateToParent(event: Event) {
      parentStateMachine?.stateMachineEventHandler?.propagateToParent(event)
        ?: onReceiveEvent(event)
    }

    fun propagateToNested(event: Event) {
      nestedStateMachineInstanceNames.forEach { name ->
        runtime.findStateMachineInstance(name)?.onReceiveEvent(event)
          ?: error("Nested state machine instance '$name' missing")
      }
    }
  }
}
