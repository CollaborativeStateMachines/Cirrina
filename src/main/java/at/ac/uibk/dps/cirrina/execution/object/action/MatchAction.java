package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class MatchAction extends Action implements EventRaisingAction {

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

  @Override
  @NotNull
  public List<Event> raises() {
    return casee
      .values()
      .stream()
      .filter(a -> a instanceof EventRaisingAction)
      .map(a -> (EventRaisingAction) a)
      .flatMap(a -> a.raises().stream())
      .toList();
  }

  public record Parameters(Expression value, Map<Expression, Action> casee) {}
}
