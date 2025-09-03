package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;
import java.util.Map;

public final class MatchAction extends Action {

  private final Expression value;

  private final Map<Expression, Action> casee;

  MatchAction(Parameters parameters) {
    this.value = parameters.value();
    this.casee = parameters.casee();
  }

  public Expression getValue() {
    return value;
  }

  public Map<Expression, Action> getCase() {
    return casee;
  }

  public record Parameters(Expression value, Map<Expression, Action> casee) {}
}
