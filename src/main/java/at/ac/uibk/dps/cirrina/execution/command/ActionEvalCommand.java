package at.ac.uibk.dps.cirrina.execution.command;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ACTION_EVAL_LATENCY;

import at.ac.uibk.dps.cirrina.execution.object.action.EvalAction;
import at.ac.uibk.dps.cirrina.utils.Time;
import com.google.common.flogger.FluentLogger;
import java.util.ArrayList;
import java.util.List;

public final class ActionEvalCommand extends ActionCommand {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final EvalAction evalAction;

  ActionEvalCommand(ExecutionContext executionContext, EvalAction evalAction) {
    super(executionContext);
    this.evalAction = evalAction;
  }

  @Override
  public List<ActionCommand> execute() throws UnsupportedOperationException {
    final var start = Time.timeInMillisecondsSinceStart();

    final var commands = new ArrayList<ActionCommand>();

    try {
      final var expression = evalAction.getExpression();

      final var extent = executionContext.scope().getExtent();

      expression.execute(extent);

      // Measure latency
      final var now = Time.timeInMillisecondsSinceStart();
      final var delta = now - start;

      final var gauges = executionContext.gauges();

      gauges.getGauge(GAUGE_ACTION_EVAL_LATENCY).set(delta);
    } catch (UnsupportedOperationException e) {
      logger.atWarning().log("Expression evaluation failed");
    }

    return commands;
  }
}
