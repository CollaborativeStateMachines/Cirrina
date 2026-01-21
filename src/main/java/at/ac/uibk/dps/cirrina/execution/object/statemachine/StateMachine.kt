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
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class StateMachine(
  private val stateMachineClass: StateMachineClass,
  private val runtime: Runtime,
  private val serviceImplementationSelector: ServiceImplementationSelector,
  private val parentStateMachine: StateMachine? = null,
) : EventListener, Scope {

  companion object {
    const val EVENT_DATA_VARIABLE_PREFIX = "$"

    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }

  val id = UUID.randomUUID().toString()

  private val readySignal = CountDownLatch(1)

  private val machineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  private val timeoutActionManager = TimeoutActionManager()
  private val stateMachineEventHandler = StateMachineEventHandler(this, runtime.eventHandler)
  private val stateInstances: Map<String, State>
  private val localContext: Context

  private var activeState: State? = null
  private var nestedStateMachineIds: List<String> = emptyList()

  init {
    runtime.phaser.register()

    localContext =
      stateMachineClass.transientContextDescription?.let {
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

  override fun onReceiveEvent(event: Event) {
    readySignal.await()

    if (isTerminated()) return

    handleEvent(event)?.let { next -> handleTransition(next, event) }

    if (event.channel == EventChannel.INTERNAL) {
      nestedStateMachineIds.forEach { nestedId ->
        runtime.findInstance(nestedId)?.onReceiveEvent(event)
          ?: error("nested state machine $nestedId not found")
      }
    }
  }

  private fun isTerminated(): Boolean =
    parentStateMachine?.isTerminated() ?: false || activeState!!.stateObject.terminal

  private fun handleTermination() {
    if (isTerminated()) {
      machineScope.cancel()
      runtime.phaser.arriveAndDeregister()
    }
  }

  private fun createCommandFactory(
    scope: Scope,
    raisingEvent: Event?,
    isWhile: Boolean = false,
  ): CommandFactory =
    CommandFactory(
      ExecutionContext(
        scope,
        serviceImplementationSelector,
        stateMachineEventHandler,
        this,
        machineScope,
        raisingEvent,
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

      val command = createCommandFactory(this, null).createActionCommand(timeout.`do`)
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

    handleTermination()
    if (isTerminated()) return null

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
    val candidateClasses =
      stateMachineClass.getOnTransitionsFromStateByEventName(activeState!!.stateObject, event.name)

    if (candidateClasses.isEmpty()) return null

    return extent
      .extend(
        event.data.fold(InMemoryContext(true)) { context, it ->
          context.apply { create(EVENT_DATA_VARIABLE_PREFIX + it.name, it.value) }
        }
      )
      .let { eventExtent ->
        trySelectTransition(candidateClasses, eventExtent)?.also {
          event.data.forEach { varData ->
            eventExtent.setOrCreate(EVENT_DATA_VARIABLE_PREFIX + varData.name, varData.value)
          }
        }
      }
  }

  fun start() {
    try {
      // Handle the initial state immediately
      doEnter(
          stateInstances[stateMachineClass.initialState.name] ?: error("initial state not found"),
          null,
        )
        ?.let { next -> handleTransition(next, null) }
    } catch (ex: Exception) {
      logger.atSevere().withCause(ex).log("fatal error in $this")
    } finally {
      readySignal.countDown()
    }
  }

  override fun toString(): String = "StateMachine(id=$id, name=${stateMachineClass.name})"

  fun setNestedStateMachineIds(ids: List<String>) {
    this.nestedStateMachineIds = ids
  }
}
