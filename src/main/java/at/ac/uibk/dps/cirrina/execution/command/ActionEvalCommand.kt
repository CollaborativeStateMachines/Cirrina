package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.EvalAction
import io.micrometer.core.instrument.MeterRegistry

/**
 * A command responsible for evaluating a specific [EvalAction] within the provided
 * [executionContext].
 *
 * This command extracts the current scope extent and executes the underlying expression. Eval
 * actions do not generate further commands.
 *
 * @property evalAction the specific action definition containing the expression to be evaluated.
 * @property executionContext the context in which the evaluation occurs.
 * @property meterRegistry the registry used for collecting metrics.
 */
class ActionEvalCommand
internal constructor(
  private val evalAction: EvalAction,
  executionContext: ExecutionContext,
  meterRegistry: MeterRegistry,
) : ActionCommand(executionContext, meterRegistry) {

  /**
   * Executes the evaluation logic.
   *
   * @return an empty list of [ActionCommand]s.
   * @throws Exception if the command execution fails due to an internal error.
   */
  override fun execute(): List<ActionCommand> =
    evalAction.expression.execute(executionContext.scope.extent).run { emptyList() }
}
