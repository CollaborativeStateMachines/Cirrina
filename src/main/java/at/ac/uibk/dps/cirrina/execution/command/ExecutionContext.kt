package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachineEventHandler
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.tracing.Counters
import at.ac.uibk.dps.cirrina.tracing.Gauges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

data class ExecutionContext(
  val scope: Scope,
  val raisingEvent: Event? = null,
  val serviceImplementationSelector: ServiceImplementationSelector,
  val eventHandler: StateMachineEventHandler,
  val eventListener: EventListener,
  val gauges: Gauges,
  val counters: Counters,
  val isWhile: Boolean = false,
  val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {

  fun close() {
    coroutineScope.cancel()
  }

  fun withScope(scope: Scope): ExecutionContext = copy(scope = scope)

  fun withIsWhile(isWhile: Boolean): ExecutionContext = copy(isWhile = isWhile)
}
