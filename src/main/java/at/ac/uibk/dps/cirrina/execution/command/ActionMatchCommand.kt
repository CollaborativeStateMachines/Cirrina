package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.MatchAction

/**
 * A command that evaluates a match expression and executes the corresponding action(s) within the
 * provided [executionContext].
 *
 * It evaluates a central value and compares it against a set of case expressions. If matches are
 * found, those actions are converted into commands; otherwise, the default action is used.
 *
 * @property matchAction the definition containing the value to match and the cases.
 * @property executionContext the context providing scope and command creation capabilities.
 */
class ActionMatchCommand(private val matchAction: MatchAction, executionContext: ExecutionContext) :
  ActionCommand(executionContext) {

  /**
   * Executes the match logic.
   *
   * @return a list of [ActionCommand]s to be executed.
   * @throws Exception if the command execution fails due to an internal error.
   */
  override fun execute(): List<ActionCommand> =
    matchAction.case.entries
      .filter { (expression, _) ->
        expression.execute(executionContext.scope.extent) ==
          matchAction.value.execute(executionContext.scope.extent)
      }
      .map { it.value }
      .ifEmpty { listOf(matchAction.default) }
      .map { action -> CommandFactory(executionContext).createActionCommand(action) }
}
