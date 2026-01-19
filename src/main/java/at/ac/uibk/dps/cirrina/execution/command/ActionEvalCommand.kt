package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.EvalAction

/**
 * A command responsible for evaluating a specific [EvalAction] within the provided
 * [executionContext].
 *
 * This command extracts the current scope extent and executes the underlying expression. Eval
 * actions do not generate further commands.
 *
 * @property executionContext the context in which the evaluation occurs.
 * @property evalAction the specific action definition containing the expression to be evaluated.
 */
class ActionEvalCommand(executionContext: ExecutionContext, private val evalAction: EvalAction) :
  ActionCommand(executionContext) {

  /**
   * Executes the evaluation logic.
   *
   * @return a [Result] containing an empty list of [ActionCommand]s on success, or a failure if the
   *   expression evaluation fails.
   */
  override fun execute(): Result<List<ActionCommand>> =
    evalAction.expression
      .execute(executionContext.scope.extent)
      .map { emptyList<ActionCommand>() }
      .recoverCatching { e -> throw IllegalStateException("could not execute eval action", e) }
}
