package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachineEventHandler
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * A context providing the necessary environment and services for the execution of [ActionCommand]s.
 *
 * This context encapsulates the current scope, event handling mechanisms, service selection logic,
 * and the coroutine lifecycle for asynchronous operations.
 *
 * @property scope the current execution scope providing access to data and extents.
 * @property raisingEvent the event currently being raised, if any.
 * @property serviceImplementationSelector the selector used to find service implementations.
 * @property eventHandler the handler used for sending events to the state machine.
 * @property eventListener the listener used for receiving internal events.
 * @property isWhile indicates whether the current execution is within a while-loop.
 * @property coroutineScope the scope managing the lifecycle of asynchronous tasks.
 */
data class ExecutionContext(
  val scope: Scope,
  val raisingEvent: Event? = null,
  val serviceImplementationSelector: ServiceImplementationSelector,
  val eventHandler: StateMachineEventHandler,
  val eventListener: EventListener,
  val isWhile: Boolean = false,
  val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {

  /** Closes the execution context by cancelling the underlying coroutine scope. */
  fun close() {
    coroutineScope.cancel()
  }

  /**
   * Creates a copy of this context with a new scope.
   *
   * @param scope the new scope to apply.
   * @return a new [ExecutionContext] instance with the updated scope.
   */
  fun withScope(scope: Scope): ExecutionContext = copy(scope = scope)

  /**
   * Creates a copy of this context with an updated loop indicator.
   *
   * @param isWhile the new loop indicator value.
   * @return a new [ExecutionContext] instance with the updated loop indicator.
   */
  fun withIsWhile(isWhile: Boolean): ExecutionContext = copy(isWhile = isWhile)
}
