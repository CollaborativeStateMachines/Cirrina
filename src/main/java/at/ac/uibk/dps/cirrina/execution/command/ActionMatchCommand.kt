package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.MatchAction

/**
 * A command that evaluates a match expression and executes the corresponding action(s) within the
 * provided [executionContext].
 *
 * It evaluates a central value and compares it against a set of case expressions. If matches are
 * found, those actions are converted into commands; otherwise, the default action is used.
 *
 * @property executionContext the context providing scope and command creation capabilities.
 * @property matchAction the definition containing the value to match and the cases.
 */
class ActionMatchCommand(executionContext: ExecutionContext, private val matchAction: MatchAction) :
  ActionCommand(executionContext) {

  /**
   * Executes the match logic.
   *
   * @return a list of [ActionCommand]s to be scheduled for execution.
   * @throws Exception if the command execution fails due to an internal error.
   */
  override fun execute(): List<ActionCommand> {
    val extent = executionContext.scope.extent
    val commandFactory = CommandFactory(executionContext)

    val conditionValue = matchAction.value.execute(extent)

    val matchingActions =
      matchAction.case.entries
        .filter { (expression, _) -> expression.execute(extent) == conditionValue }
        .map { it.value }

    val finalActions = matchingActions.ifEmpty { listOf(matchAction.default) }

    return finalActions.map { action -> commandFactory.createActionCommand(action) }
  }
}
