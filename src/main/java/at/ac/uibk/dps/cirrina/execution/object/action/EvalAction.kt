package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;

/**
 * Eval action, evaluates an expression.
 */
public final class EvalAction extends Action {

  private final Expression expression;

  /**
   * Initializes this assign action.
   *
   * @param parameters initialization parameters
   */
  EvalAction(Parameters parameters) {
    this.expression = parameters.expression();
  }

  /**
   * Returns the expression.
   *
   * @return expression
   */
  public Expression getExpression() {
    return expression;
  }

  public record Parameters(Expression expression) {}
}
