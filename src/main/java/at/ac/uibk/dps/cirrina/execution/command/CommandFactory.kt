package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.EvalAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.InvokeAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.MatchAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.RaiseAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutResetAction

/**
 * A factory responsible for creating [ActionCommand] instances from [Action] definitions.
 *
 * This factory maps action definitions to their corresponding executable command implementations
 * within the provided [executionContext].
 *
 * @property executionContext the context to be injected into the created commands.
 */
class CommandFactory(private val executionContext: ExecutionContext) {

  /**
   * Creates an [ActionCommand] for the given [action].
   *
   * @param action the action definition to convert.
   * @return the corresponding [ActionCommand] implementation.
   * @throws Exception if the command execution fails due to an internal error.
   */
  fun createActionCommand(action: Action): ActionCommand =
    when (action) {
      is EvalAction -> ActionEvalCommand(action, executionContext)
      is InvokeAction -> ActionInvokeCommand(action, executionContext)
      is MatchAction -> ActionMatchCommand(action, executionContext)
      is RaiseAction -> ActionRaiseCommand(action, executionContext)
      is TimeoutAction -> ActionTimeoutCommand(action, executionContext)
      is TimeoutResetAction -> ActionTimeoutResetCommand(action, executionContext)
      else -> error("unexpected action type: ${action::class.simpleName}")
    }
}
