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
import com.google.common.flogger.FluentLogger
import java.util.*
import kotlinx.coroutines.channels.Channel

class StateMachine(
  private val runtime: Runtime,
  val stateMachineClass: StateMachineClass,
  private val serviceImplementationSelector: ServiceImplementationSelector,
  private val parentStateMachine: StateMachine? = null,
) : EventListener, Scope {

  companion object {
    const val EVENT_DATA_VARIABLE_PREFIX = "$"
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }

  private val stateMachineId = UUID.randomUUID().toString()
  private val timeoutActionManager = TimeoutActionManager()
  private val eventQueue = Channel<Event>(Channel.UNLIMITED)
  private val stateMachineEventHandler = StateMachineEventHandler(this, runtime.eventHandler)
  private val stateInstances: Map<String, State>
  private val localContext: Context

  private var activeState: State? = null
  private var nestedStateMachineIds: List<String> = emptyList()

  init {
    localContext =
      stateMachineClass.localContextDescription?.let {
        ContextBuilder.from(it).build().getOrThrow()
      } ?: ContextBuilder.empty().inMemoryContext(true).build().getOrThrow()

    stateInstances =
      stateMachineClass.vertexSet().associate { stateClass ->
        stateClass.name to State(stateClass, this)
      }
  }

  override val extent: Extent by lazy {
    parentStateMachine?.extent?.extend(localContext) ?: runtime.extent.extend(localContext)
  }

  override val id: String
    get() = stateMachineId

  override fun onReceiveEvent(event: Event): Boolean {
    if (isTerminated()) return false
    eventQueue.trySend(event)

    if (event.channel == EventChannel.INTERNAL) {
      nestedStateMachineIds.forEach { nestedId ->
        runtime.findInstance(nestedId)?.onReceiveEvent(event)
          ?: error("nested state machine $nestedId not found")
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
        isWhile,
      )
    )

  private fun trySelectTransition(
    transitionObjects: List<TransitionClass>,
    extent: Extent,
  ): Transition? {
    val selected =
      transitionObjects.mapNotNull { transitionObject ->
        val hasOr = transitionObject.or != null
        val result = transitionObject.evaluate(extent)
        if (hasOr || result) Transition(transitionObject, hasOr && !result) else null
      }

    return when (selected.size) {
      0 -> null
      1 -> selected.first()
      else -> error("non-determinism detected: multiple transitions match")
    }
  }

  private fun executeCommands(commands: List<ActionCommand>) {
    commands.forEach { command ->
      val nextCommands = command.execute()
      if (command is ActionTimeoutResetCommand) {
        stopTimeoutAction(command.timeoutResetAction.action)
      }
      executeCommands(nextCommands)
    }
  }

  private fun startTimeoutActions(timeoutActions: List<TimeoutAction>) {
    timeoutActions.forEach { timeout ->
      val delay = timeout.delay.execute(extent)
      if (delay !is Number) {
        error("delay expression '${timeout.delay}' evaluated to non-numeric: $delay")
      }

      val command = createCommandFactory(this, null).createActionCommand(timeout.action)
      if (command !is ActionRaiseCommand) {
        error("a timeout action must be a raise action, found: ${command::class.simpleName}")
      }

      timeoutActionManager.start(timeout.name, delay) { executeCommands(listOf(command)) }
    }
  }

  private fun stopTimeoutAction(name: String) = timeoutActionManager.stop(name)

  private fun stopAllTimeoutActions() = timeoutActionManager.stopAll()

  private fun doExit(state: State, event: Event?) {
    stopAllTimeoutActions()
    val commands = state.getExitActionCommands(createCommandFactory(state, event))
    executeCommands(commands)
  }

  private fun doTransition(transition: Transition, event: Event?) {
    if (transition.isOr) return
    val commands = transition.getActionCommands(createCommandFactory(this, event))
    executeCommands(commands)
  }

  private fun doEnter(state: State, event: Event?): Transition? {
    executeCommands(state.getEntryActionCommands(createCommandFactory(state, event)))
    executeCommands(state.getWhileActionCommands(createCommandFactory(state, event, true)))

    startTimeoutActions(state.getTimeoutActionObjects())

    check(stateInstances.containsValue(state)) {
      "state '${state.stateObject.name}' does not exist"
    }
    activeState = state

    val alwaysTransitions = stateMachineClass.getAlwaysTransitionsFromState(state.stateObject)
    return trySelectTransition(alwaysTransitions, extent)
  }

  private fun handleTransition(transition: Transition, event: Event?) {
    if (transition.isInternal) {
      doTransition(transition, event)
    } else {
      val targetName = transition.targetStateName ?: error("target state name missing")
      val targetState = stateInstances[targetName] ?: error("target state '$targetName' not found")

      doExit(activeState!!, event)
      doTransition(transition, event)

      val next = doEnter(targetState, event)
      next?.let { handleTransition(it, event) }
    }
  }

  private fun handleEvent(event: Event): Transition? {
    val eventDataContext = InMemoryContext(true)
    event.data.forEach { eventDataContext.create(EVENT_DATA_VARIABLE_PREFIX + it.name, it.value) }

    val eventExtent = extent.extend(eventDataContext)
    val onTransitions =
      stateMachineClass.getOnTransitionsFromStateByEventName(activeState!!.stateObject, event.name)
    val selected = trySelectTransition(onTransitions, eventExtent)

    selected?.let {
      event.data.forEach { varData ->
        eventExtent.setOrCreate(EVENT_DATA_VARIABLE_PREFIX + varData.name, varData.value)
      }
    }
    return selected
  }

  suspend fun start() {
    try {
      val initialState =
        stateInstances[stateMachineClass.initialState.name]
          ?: error("Initial state '${stateMachineClass.initialState.name}' not found")

      var nextTransition: Transition? = doEnter(initialState, null)

      while (!isTerminated()) {
        var currentEvent: Event? = null

        if (nextTransition == null) {
          currentEvent = eventQueue.receive()
          nextTransition = handleEvent(currentEvent)
        }

        nextTransition?.let {
          handleTransition(it, currentEvent)
          nextTransition = null
        }
      }
      logger.atInfo().log("State machine '$this' stopped successfully")
    } catch (ex: Exception) {
      logger.atSevere().withCause(ex).log("State machine '$this' encountered a fatal error")
      // Re-throw if we need parents to know, or just let it die here as a terminal failure
    }
  }

  override fun toString(): String = "StateMachine(id=$id, name=${stateMachineClass.name})"

  fun setNestedStateMachineIds(ids: List<String>) {
    this.nestedStateMachineIds = ids
  }
}
