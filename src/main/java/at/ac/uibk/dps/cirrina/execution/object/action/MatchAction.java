package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class MatchAction extends Action implements EventRaisingAction {

  private final Expression value;

  private final Map<Expression, Action> casee;

  private final @Nullable Action defaultt;

  MatchAction(Parameters parameters) {
    this.value = parameters.value();
    this.casee = parameters.casee();
    this.defaultt = parameters.defaultt();
  }

  public Expression getValue() {
    return value;
  }

  public Map<Expression, Action> getCase() {
    return casee;
  }

  public @Nullable Action getDefault() {
    return defaultt;
  }

  @Override
  @NotNull
  public List<Event> raises() {
    return Stream.concat(
      casee
        .values()
        .stream()
        .filter(EventRaisingAction.class::isInstance)
        .map(EventRaisingAction.class::cast)
        .flatMap(a -> a.raises().stream()),
      Optional.ofNullable(defaultt)
        .filter(EventRaisingAction.class::isInstance)
        .map(EventRaisingAction.class::cast)
        .stream()
        .flatMap(a -> a.raises().stream())
    ).toList();
  }

  public record Parameters(
    Expression value,
    Map<Expression, Action> casee,
    @Nullable Action defaultt
  ) {}
}
