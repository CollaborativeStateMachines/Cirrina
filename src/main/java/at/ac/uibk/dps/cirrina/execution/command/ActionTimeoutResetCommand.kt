package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutResetAction

class ActionTimeoutResetCommand
internal constructor(
  executionContext: ExecutionContext,
  val timeoutResetAction: TimeoutResetAction,
) : ActionCommand(executionContext) {

  override fun execute(): Result<List<ActionCommand>> = runCatching { emptyList() }
}
