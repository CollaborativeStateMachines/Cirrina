package at.ac.uibk.dps.cirrina.execution.`object`.statemachine

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.command.*
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.*
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.state.State
import at.ac.uibk.dps.cirrina.execution.`object`.transition.Transition
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.tracing.Counters
import at.ac.uibk.dps.cirrina.tracing.Gauges
import at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*
import at.ac.uibk.dps.cirrina.utils.Id
import at.ac.uibk.dps.cirrina.utils.Time
import com.google.common.flogger.FluentLogger
import io.opentelemetry.api.OpenTelemetry
import java.util.concurrent.ConcurrentLinkedQueue

class StateMachine(
  private val runtime: Runtime,
  val stateMachineClass: StateMachineClass,
  private val serviceImplementationSelector: ServiceImplementationSelector,
  openTelemetry: OpenTelemetry,
  private val parentStateMachine: StateMachine? = null,
) : Runnable, EventListener, Scope {

  companion object {
    const val EVENT_DATA_VARIABLE_PREFIX = "$"
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }

  private val id = Id()
  private val timeoutActionManager = TimeoutActionManager()
  private val eventQueue = ConcurrentLinkedQueue<Event>()
  private val stateMachineEventHandler = StateMachineEventHandler(this, runtime.eventHandler)
  private val gauges: Gauges
  private val counters: Counters
  private val stateInstances: Map<String, State>
  private val localContext: Context

  private var activeState: State? = null
  private var nestedStateMachineIds: List<Id> = emptyList()

  init {
    // Build local context
    localContext =
      stateMachineClass.localContextDescription?.let {
        ContextBuilder.from(it).build().getOrThrow()
      } ?: ContextBuilder.empty().inMemoryContext(true).build().getOrThrow()

    // Construct state instances
    stateInstances =
      stateMachineClass.vertexSet().associate { stateClass ->
        stateClass.name to State(stateClass, this)
      }

    // Metrics setup
    val meter = openTelemetry.getMeter("stateMachine-${id}")
    gauges =
      Gauges(meter, getId()).apply {
        listOf(
            GAUGE_EVENT_RESPONSE_TIME_EXCLUSIVE,
            GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE,
            GAUGE_ACTION_DATA_LATENCY,
            GAUGE_ACTION_INVOKE_LATENCY,
            GAUGE_ACTION_RAISE_LATENCY,
            GAUGE_ACTION_EVAL_LATENCY,
          )
          .forEach { addGauge(it) }
      }

    counters =
      Counters(meter, getId()).apply {
        listOf(
            COUNTER_EVENTS_RECEIVED,
            COUNTER_EVENTS_HANDLED,
            COUNTER_INVOCATIONS,
            COUNTER_STATE_MACHINE_INSTANCES,
          )
          .forEach { addCounter(it) }
      }
  }

  override fun onReceiveEvent(event: Event): Boolean {
    if (isTerminated()) return false

    counters
      .getCounter(COUNTER_EVENTS_RECEIVED)
      .add(1, counters.attributesForEvent(event.channel.toString()))

    eventQueue.add(event)

    synchronized(this) { (this as Object).notify() }

    if (event.channel == EventChannel.INTERNAL) {
      nestedStateMachineIds.forEach { nestedId ->
        val nestedInstance =
          runtime.findInstance(nestedId)
            ?: throw IllegalStateException(
              "nested state machine could not be found, could not propagate event"
            )
        nestedInstance.onReceiveEvent(event)
      }
    }
    return true
  }

  fun isTerminated(): Boolean {
    val currentActive = activeState ?: return false
    if (parentStateMachine?.isTerminated() == true) return true
    return currentActive.stateObject.isTerminal
  }

  private fun createCommandFactory(
    scope: Scope,
    raisingEvent: Event?,
    isWhile: Boolean = false,
  ): CommandFactory =
    CommandFactory(
      ExecutionContext(
        scope,
        raisingEvent,
        serviceImplementationSelector,
        stateMachineEventHandler,
        this,
        gauges,
        counters,
        isWhile,
      )
    )

  private fun trySelectTransition(
    transitionObjects: List<TransitionClass>,
    extent: Extent,
  ): Result<Transition?> =
    runCatching {
        val selected =
          transitionObjects.mapNotNull { transitionObject ->
            val hasOr = transitionObject.or != null
            val result = transitionObject.evaluate(extent).getOrThrow()
            if (hasOr || result) Transition(transitionObject, hasOr && !result) else null
          }

        when (selected.size) {
          0 -> null
          1 -> selected.first()
          else -> throw IllegalStateException("non-determinism detected")
        }
      }
      .recoverCatching { ex -> throw IllegalStateException("no transition could be selected", ex) }

  private fun executeCommands(commands: List<ActionCommand>): Result<Unit> =
    runCatching {
        commands.forEach { command ->
          val nextCommands = command.execute().getOrThrow()
          if (command is ActionTimeoutResetCommand) {
            stopTimeoutAction(command.timeoutResetAction.action)
          }
          executeCommands(nextCommands).getOrThrow()
        }
      }
      .recoverCatching { ex ->
        throw UnsupportedOperationException("could not execute action commands", ex)
      }

  private fun startTimeoutActions(timeoutActions: List<TimeoutAction>): Result<Unit> = runCatching {
    timeoutActions.forEach { timeout ->
      val delay = timeout.delay.execute(getExtent()).getOrThrow()
      if (delay !is Number) {
        throw UnsupportedOperationException(
          "the delay expression '${timeout.delay}' did not evaluate to a numeric value"
        )
      }

      val command = createCommandFactory(this, null).createActionCommand(timeout.action)
      if (command !is ActionRaiseCommand) {
        throw IllegalArgumentException("a timeout action must be a raise action")
      }

      timeoutActionManager.start(timeout.name, delay) { executeCommands(listOf(command)) }
    }
  }

  private fun stopTimeoutAction(name: String) = timeoutActionManager.stop(name)

  private fun stopAllTimeoutActions() = timeoutActionManager.stopAll()

  private fun doExit(state: State, event: Event?): Result<Unit> {
    stopAllTimeoutActions()
    // TODO: Cancel while actions
    val commands = state.getExitActionCommands(createCommandFactory(state, event))
    return executeCommands(commands)
  }

  private fun doTransition(transition: Transition, event: Event?): Result<Unit> {
    if (transition.isOr) return Result.success(Unit)
    val commands = transition.getActionCommands(createCommandFactory(this, event))
    return executeCommands(commands)
  }

  private fun doEnter(state: State, event: Event?): Result<Transition?> = runCatching {
    executeCommands(state.getEntryActionCommands(createCommandFactory(state, event))).getOrThrow()
    executeCommands(state.getWhileActionCommands(createCommandFactory(state, event, true)))
      .getOrThrow()

    startTimeoutActions(state.getTimeoutActionObjects()).getOrThrow()

    if (!stateInstances.containsValue(state)) {
      throw IllegalArgumentException("a state '${state.stateObject.name}' does not exist")
    }
    activeState = state

    val alwaysTransitions = stateMachineClass.findAlwaysTransitionsFromState(state.stateObject)
    trySelectTransition(alwaysTransitions, getExtent()).getOrThrow()
  }

  private fun handleTransition(transition: Transition, event: Event?): Result<Unit> = runCatching {
    if (transition.isInternal) {
      doTransition(transition, event).getOrThrow()
    } else {
      val targetName =
        transition.targetStateName ?: error("target state name missing for external transition")
      val targetState =
        stateInstances[targetName] ?: error("target state '$targetName' cannot be found")

      doExit(activeState!!, event).getOrThrow()
      doTransition(transition, event).getOrThrow()

      val next = doEnter(targetState, event).getOrThrow()
      next?.let { handleTransition(it, event).getOrThrow() }
    }
  }

  private fun handleEvent(event: Event): Result<Transition?> = runCatching {
    counters
      .getCounter(COUNTER_EVENTS_HANDLED)
      .add(1, counters.attributesForEvent(event.channel.toString()))

    val eventDataContext = InMemoryContext(true)
    event.data.forEach {
      eventDataContext.create(EVENT_DATA_VARIABLE_PREFIX + it.name, it.value).getOrThrow()
    }

    val extent = getExtent().extend(eventDataContext)
    val onTransitions =
      stateMachineClass.findOnTransitionsFromStateByEventName(activeState!!.stateObject, event.name)
    val selected = trySelectTransition(onTransitions, extent).getOrThrow()

    selected?.let {
      event.data.forEach { varData ->
        getExtent()
          .setOrCreate(EVENT_DATA_VARIABLE_PREFIX + varData.name, varData.value)
          .getOrNull()
      }
    }
    selected
  }

  override fun run() {
    logger.atInfo().log("State machine '$this': Starting")
    counters.getCounter(COUNTER_STATE_MACHINE_INSTANCES).add(1, counters.attributesForInstances())

    try {
      val initialState = stateInstances[stateMachineClass.initialState.name]!!
      var nextTransition = doEnter(initialState, null).getOrThrow()

      while (!isTerminated()) {
        var currentEvent: Event? = null

        if (nextTransition == null) {
          synchronized(this) {
            while (eventQueue.isEmpty()) {
              (this as Object).wait()
            }
            currentEvent = eventQueue.poll()
          }
          nextTransition = handleEvent(currentEvent!!).getOrThrow()
        }

        nextTransition?.let {
          handleTransition(it, currentEvent).getOrThrow()
          nextTransition = null
        }

        currentEvent?.let { event ->
          val delta = Time.timeInMillisecondsSinceEpoch() - event.createdTime
          gauges
            .getGauge(GAUGE_EVENT_RESPONSE_TIME_EXCLUSIVE)
            .set(delta, gauges.attributesForEvent(event.channel.toString()))
        }
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.atSevere().withCause(e).log("State machine '$this': Interrupted")
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("State machine '$this': Received a fatal error")
    } finally {
      logger.atInfo().log("State machine '$this': Stopped")
      counters
        .getCounter(COUNTER_STATE_MACHINE_INSTANCES)
        .add(-1, counters.attributesForInstances())
    }
  }

  override fun getExtent(): Extent =
    parentStateMachine?.getExtent()?.extend(localContext) ?: runtime.extent.extend(localContext)

  override fun getId(): String = id.toString()

  override fun toString(): String = "StateMachine(id=$id, name=${stateMachineClass.name})"

  fun getStateMachineInstanceId(): Id = id

  fun setNestedStateMachineIds(ids: List<Id>) {
    this.nestedStateMachineIds = ids
  }
}
