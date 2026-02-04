package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import kotlinx.coroutines.CoroutineScope

data class CommandExecutionContext(
  val scope: Scope,
  val serviceImplementationSelector: ServiceImplementationSelector,
  val stateMachineEventHandler: StateMachine.StateMachineEventHandler,
  val coroutineScope: CoroutineScope,
  val raisingEvent: Event? = null,
  val isWhile: Boolean = false,
)
