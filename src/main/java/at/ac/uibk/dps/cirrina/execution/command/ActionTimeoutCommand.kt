package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction

class ActionTimeoutCommand
internal constructor(executionContext: ExecutionContext, private val timeoutAction: TimeoutAction) :
  ActionCommand(executionContext) {

  /**
   * Executes the timeout action by delegating to the inner action.
   *
   * @return A [Result] containing a list with the single command generated from the inner action.
   */
  override fun execute(): Result<List<ActionCommand>> = runCatching {
    val commandFactory = CommandFactory(executionContext)

    // Create the command for the nested action defined within the timeout
    val innerCommand = commandFactory.createActionCommand(timeoutAction.action)

    listOf(innerCommand)
  }
}
