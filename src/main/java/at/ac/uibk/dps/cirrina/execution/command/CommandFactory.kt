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
 * This factory maps domain-specific action definitions to their corresponding executable command
 * implementations within the provided [executionContext].
 *
 * @property executionContext the context to be injected into the created commands.
 */
class CommandFactory(private val executionContext: ExecutionContext) {

  /**
   * Creates an [ActionCommand] for the given [action].
   *
   * @param action the action definition to convert.
   * @return a [Result] containing the corresponding [ActionCommand] implementation on success, or a
   *   failure if the action type is unexpected.
   */
  fun createActionCommand(action: Action): ActionCommand =
    when (action) {
      is EvalAction -> ActionEvalCommand(executionContext, action)
      is InvokeAction -> ActionInvokeCommand(executionContext, action)
      is MatchAction -> ActionMatchCommand(executionContext, action)
      is RaiseAction -> ActionRaiseCommand(executionContext, action)
      is TimeoutAction -> ActionTimeoutCommand(executionContext, action)
      is TimeoutResetAction -> ActionTimeoutResetCommand(executionContext, action)
      else -> error("unexpected action type: ${action::class.simpleName}")
    }
}
