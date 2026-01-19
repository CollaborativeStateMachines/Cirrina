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
   * @return a [Result] containing a list of [ActionCommand]s to be scheduled for execution on
   *   success, or a failure if an expression evaluation or command creation fails.
   */
  override fun execute(): Result<List<ActionCommand>> {
    val extent = executionContext.scope.extent
    val commandFactory = CommandFactory(executionContext)

    return matchAction.value
      .execute(extent)
      .mapCatching { conditionValue ->
        // Matching cases
        val matchingActions =
          matchAction.case.entries
            .filter { (expression, _) -> expression.execute(extent).getOrThrow() == conditionValue }
            .map { it.value }

        // Default case
        val finalActions = matchingActions.ifEmpty { listOf(matchAction.default) }

        finalActions.map { commandFactory.createActionCommand(it).getOrThrow() }
      }
      .recoverCatching { e ->
        throw UnsupportedOperationException("could not execute match action", e)
      }
  }
}
