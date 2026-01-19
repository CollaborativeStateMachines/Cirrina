package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.EvalAction
import at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ACTION_EVAL_LATENCY
import at.ac.uibk.dps.cirrina.utils.Time
import com.google.common.flogger.FluentLogger

class ActionEvalCommand(executionContext: ExecutionContext, private val evalAction: EvalAction) :
  ActionCommand(executionContext) {

  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }

  override fun execute(): Result<List<ActionCommand>> =
    runCatching {
        val start = Time.timeInMillisecondsSinceStart()
        val extent = executionContext.scope.getExtent()

        // Execute the expression and propagate failure if necessary
        evalAction.expression.execute(extent).getOrThrow()

        // Measure and record latency
        val delta = Time.timeInMillisecondsSinceStart() - start
        executionContext.gauges.getGauge(GAUGE_ACTION_EVAL_LATENCY).set(delta)

        // Eval actions typically don't produce follow-up commands
        emptyList<ActionCommand>()
      }
      .recoverCatching { ex ->
        logger.atWarning().withCause(ex).log("expression evaluation failed")
        throw UnsupportedOperationException("expression evaluation failed", ex)
      }
}
