package at.ac.uibk.dps.cirrina.execution.object.guard;

import static at.ac.uibk.dps.cirrina.cirrina.Cirrina.logging;
import static at.ac.uibk.dps.cirrina.cirrina.Cirrina.tracer;
import static at.ac.uibk.dps.cirrina.cirrina.Cirrina.tracing;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*;

import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.Map;

/**
 * Guard, represents an evaluable guard that yields a boolean return value.
 */
public class Guard {

  /**
   * Guard expression.
   */
  private final Expression expression;

  /**
   * Initializes this guard object.
   * <p>
   * The name can be empty in case the guard is in-line.
   *
   * @param expression Expression of the guard.
   */
  Guard(Expression expression) {
    this.expression = expression;
  }

  /**
   * Evaluate this guard and return the resulting boolean value.
   *
   * @param extent Extent describing variables in scope.
   * @return Boolean result.
   * @throws UnsupportedOperationException If the guard expression could not be executed.
   * @throws IllegalArgumentException      If the expression could not be evaluated, or the expression does not produce a boolean value.
   */
  public boolean evaluate(Extent extent, TracingAttributes tracingAttributes, Span parentSpan) throws IllegalArgumentException, UnsupportedOperationException {
    logging.logGuardEvaluation(expression.toString(), tracingAttributes.getStateMachineName(), tracingAttributes.getStateMachineId());
    Span span = tracing.initializeSpan("Guard Evaluation: " + expression.toString(), tracer, parentSpan,
        Map.of( ATTR_GUARD_EXPRESSION, expression.toString(),
            ATTR_STATE_MACHINE_ID, tracingAttributes.getStateMachineId(),
            ATTR_STATE_MACHINE_NAME, tracingAttributes.getStateMachineName(),
            ATTR_PARENT_STATE_MACHINE_NAME, tracingAttributes.getParentStateMachineName(),
            ATTR_PARENT_STATE_MACHINE_ID, tracingAttributes.getParentStateMachineId()));
    try(Scope scope = span.makeCurrent()) {
      var result = expression.execute(extent);

      if (!(result instanceof Boolean)) {
        throw new IllegalArgumentException("Guard expression '%s' does not produce a boolean value".formatted(expression));
      }

      return (Boolean) result;
      
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      tracing.recordException(e, span);
      logging.logExeption(tracingAttributes.getStateMachineId(), e, tracingAttributes.getStateMachineName());
      throw e;
    } finally {
      span.end();
    }
  }

  /**
   * Returns the expression.
   *
   * @return Expression.
   */
  public Expression getExpression() {
    return expression;
  }
}
