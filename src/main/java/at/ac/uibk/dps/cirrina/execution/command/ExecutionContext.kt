package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import kotlinx.coroutines.CoroutineScope

/**
 * A context providing the necessary environment and services for the execution of [ActionCommand]s.
 *
 * This context encapsulates the current scope, event handling mechanisms, service selection logic,
 * and the coroutine lifecycle for asynchronous operations.
 *
 * @property scope the current execution scope providing access to data and extents.
 * @property serviceImplementationSelector the selector used to find service implementations.
 * @property stateMachineEventHandler the handler used for sending events within the state machine.
 * @property eventListener the listener used for receiving internal events.
 * @property coroutineScope the scope managing the lifecycle of asynchronous tasks.
 * @property raisingEvent the event currently being raised, if any.
 * @property isWhile indicates whether the current execution is within a while-loop.
 */
data class ExecutionContext(
  val scope: Scope,
  val serviceImplementationSelector: ServiceImplementationSelector,
  val stateMachineEventHandler: StateMachine.StateMachineEventHandler,
  val eventListener: EventListener,
  val coroutineScope: CoroutineScope,
  val raisingEvent: Event? = null,
  val isWhile: Boolean = false,
)
