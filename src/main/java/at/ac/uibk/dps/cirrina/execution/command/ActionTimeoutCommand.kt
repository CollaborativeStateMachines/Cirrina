package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction

/**
 * A command responsible for delegating execution to a nested action within a timeout context as
 * defined by a [timeoutAction].
 *
 * This command creates a single inner command from the nested action. It does not perform the
 * timeout logic itself but rather prepares the command hierarchy for the execution engine.
 *
 * @property executionContext the context in which the evaluation occurs.
 * @property timeoutAction the specific action definition containing the inner action.
 */
class ActionTimeoutCommand
internal constructor(executionContext: ExecutionContext, private val timeoutAction: TimeoutAction) :
  ActionCommand(executionContext) {
  /**
   * Executes the timeout logic by delegating to the inner action.
   *
   * @return a list with the single [ActionCommand] generated from the inner action.
   * @throws Exception if the command execution fails due to an internal error.
   */
  override fun execute(): List<ActionCommand> {
    val innerCommand = CommandFactory(executionContext).createActionCommand(timeoutAction.action)

    return listOf(innerCommand)
  }
}
