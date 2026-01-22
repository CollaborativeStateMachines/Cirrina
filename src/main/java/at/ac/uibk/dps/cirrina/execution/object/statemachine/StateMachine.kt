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
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val VAR_PREFIX = "$"

class StateMachine(
  private val stateMachineClass: StateMachineClass,
  private val runtime: Runtime,
  private val serviceImplementationSelector: ServiceImplementationSelector,
  private val parentStateMachine: StateMachine? = null,
) : EventListener, Scope {

  /** The unique identifier of this state machine. */
  val id: String = UUID.randomUUID().toString()

  // Signal indicating that the state machine is ready to start
  private val readySignal = CountDownLatch(1)

  // Coroutine scope for timeout actions and scoped coroutines
  private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  // Timeout manager
  private val timeoutManager = TimeoutActionManager(coroutineScope)

  // Event handler
  private val eventHandler = StateMachineEventHandler(this, runtime.eventHandler)

  // State instances
  private val stateInstances =
    stateMachineClass.vertexSet().associate { it.name to State(it, this) }

  // Current state
  private var activeState: State? = null

  // List of nested state machine ids
  private var nestedIds = emptyList<String>()

  /**
   * The current extent of the state machine, created by extending the parent's extent with the
   * state machine's transient context.
   */
  override val extent: Extent by lazy {
    parentStateMachine?.extent?.extend(buildTransientContext())
      ?: runtime.extent.extend(buildTransientContext())
  }

  init {
    runtime.phaser.register()
  }

  /** Starts the state machine. */
  fun start() =
    runCatching {
        stateInstances[stateMachineClass.initialState.name] ?: error("initial state not found")
      }
      .onSuccess { initial ->
        logger.info { "$this starting" }

        // Enter the initial state and take the first transition if present
        doEnter(initial, null)?.let { step(it, null) }
      }
      .onFailure { logger.error(it) { "$this fatal error" } }
      .also {
        // Indicate that the state machine is ready
        readySignal.countDown()
      }

  /** Handles the received [event]. */
  override fun onReceiveEvent(event: Event) {
    readySignal.await()
    takeIf { !isTerminated() }
      ?.run {
        logger.debug { event }

        // Handle the event and progress if a transition is present
        handleEvent(event)?.let { step(it, event) }

        // Propagate the event to nested state machines
        propagate(event)
      }
  }

  /** Sets the nested state machine ids. */
  fun setNestedStateMachineIds(ids: List<String>) {
    this.nestedIds = ids
  }

  private tailrec fun step(transition: Transition, event: Event?) {
    // Internal transition
    if (transition.isInternal) {
      logger.debug { "$this internal transition: $transition" }
      doTransition(transition, event)
      return
    }

    // Acquire the target state
    val target = stateInstances[transition.targetStateName] ?: error("target not found")
    logger.debug { "$this transitioning: $activeState -> $target" }

    // Execute exit and transition actions
    doExit(activeState!!, event)
    doTransition(transition, event)

    // Execute entry actions and progress if a subsequent transition is present
    val next = doEnter(target, event)
    if (next != null) step(next, event)
  }

  private fun handleEvent(event: Event): Transition? =
    activeState?.let { current ->
      // Find candidate transitions
      val candidates =
        stateMachineClass.getOnTransitionsFromStateByEventName(current.stateClass, event.name)
      if (candidates.isEmpty()) return null

      // Build a temporary extent for transition evaluation
      val evalExtent =
        extent.extend(
          event.data.fold(InMemoryContext()) { ctx, it ->
            ctx.apply { create(VAR_PREFIX + it.name, it.value) }
          }
        )

      // If transition evaluation succeeds, set the context variables and take the transition
      trySelect(candidates, evalExtent)?.also {
        event.data.forEach { d -> extent.setOrCreate(VAR_PREFIX + d.name, d.value) }
      }
    }

  private fun doEnter(state: State, event: Event?): Transition? =
    state
      .also {
        logger.info { "$this entering: $it" }

        // Switch state
        activeState = it

        // Execute entry and while actions
        createFactory(it, event).let { f ->
          execute(it.getEntryActionCommands(f))
          execute(it.getWhileActionCommands(createFactory(it, event, true)))
        }

        // Start timeout actions
        it.getTimeoutActionObjects().forEach(::startTimeout)

        // Check termination
        checkTermination()
      }
      .takeIf { !isTerminated() }
      ?.let { trySelect(stateMachineClass.getAlwaysTransitionsFromState(it.stateClass), extent) }

  private fun doExit(state: State, event: Event?) =
    state.run {
      logger.debug { "$this exiting: $state" }

      // Stop all timeout actions
      timeoutManager.stopAll()

      // Execute exit actions
      execute(getExitActionCommands(createFactory(this, event)))
    }

  private fun doTransition(transition: Transition, event: Event?) {
    // Don't execute actions for default transitions
    if (!transition.isOr) execute(transition.getActionCommands(createFactory(this, event)))
  }

  private fun trySelect(transitions: List<TransitionClass>, evalExtent: Extent): Transition? =
    transitions
      // Take those whose condition evaluates to true, or default transitions
      .mapNotNull { tc ->
        val result = tc.evaluate(evalExtent)
        when {
          result -> Transition(tc, isOr = false)
          tc.or != null -> Transition(tc, isOr = true)
          else -> null
        }
      }
      // Return the only transition
      .let { selected ->
        when (selected.size) {
          0 -> null
          1 -> selected.first().also { logger.trace { "$this selected: $it" } }
          else -> error("non-determinism detected in $this")
        }
      }

  private tailrec fun execute(commands: List<ActionCommand>) {
    if (commands.isEmpty()) return

    // Execute all commands, possibly producing new commands
    val nextBatch =
      commands.flatMap { cmd ->
        cmd.execute().also {
          (cmd as? ActionTimeoutResetCommand)?.let { stopTimeout(it.timeoutResetAction.action) }
        }
      }
    execute(nextBatch)
  }

  private fun startTimeout(timeout: TimeoutAction) {
    // Evaluate the delay expression
    val delay = timeout.delay.execute(extent) as? Number ?: error("non-numeric delay")

    logger.debug { "$this starting: $timeout" }

    // Start the timeout coroutine
    timeoutManager.start(timeout.name, delay) {
      val cmd = createFactory(this, null).createActionCommand(timeout.`do`)
      execute(listOf(cmd as? ActionRaiseCommand ?: error("must be raise")))
    }
  }

  private fun stopTimeout(name: String) =
    logger.debug { "$this stopping timeout: $name" }.also { timeoutManager.stop(name) }

  private fun isTerminated(): Boolean =
    parentStateMachine?.isTerminated() ?: false || activeState?.stateClass?.terminal == true

  private fun checkTermination() {
    if (isTerminated()) {
      logger.info { "$this terminated" }

      // Stop timeouts
      timeoutManager.shutdown()

      // Stop coroutines
      coroutineScope.cancel()

      // Indicate termination
      runtime.phaser.arriveAndDeregister()
    }
  }

  private fun propagate(event: Event) {
    if (event.channel == EventChannel.INTERNAL) {
      nestedIds.forEach {
        runtime.findInstance(it)?.onReceiveEvent(event) ?: error("nested $it missing")
      }
    }
  }

  private fun buildTransientContext() =
    stateMachineClass.transientContextDescription?.let {
      ContextBuilder.from(it).inMemoryContext().build().getOrThrow()
    } ?: ContextBuilder.empty().inMemoryContext().build().getOrThrow()

  private fun createFactory(scope: Scope, event: Event?, isWhile: Boolean = false) =
    CommandFactory(
      ExecutionContext(
        scope,
        serviceImplementationSelector,
        eventHandler,
        this,
        coroutineScope,
        event,
        isWhile,
      )
    )

  override fun toString() = "StateMachine(id=$id, name=${stateMachineClass.name})"
}
