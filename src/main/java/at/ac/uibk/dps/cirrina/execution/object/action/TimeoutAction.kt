package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Timeout action object.
 */
public final class TimeoutAction extends Action implements EventRaisingAction {

  /**
   * The name of the timeout action.
   */
  private final String name;

  /**
   * The delay expression. Needs to evaluate to a numeric value, representing the delay in milliseconds.
   *
   * @see Number
   */
  private final Expression delay;

  /**
   * The action to execute after the timeout.
   */
  private final Action action;

  /**
   * Initializes this timeout action object.
   *
   * @param parameters Parameters.
   */
  TimeoutAction(Parameters parameters) {
    this.name = parameters.name;
    this.delay = parameters.delay;
    this.action = parameters.action;
  }

  /**
   * Returns the name.
   *
   * @return Name.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the delay expression.
   *
   * @return Delay expression.
   */
  public Expression getDelay() {
    return delay;
  }

  /**
   * Returns the action.
   *
   * @return Action.
   */
  public Action getAction() {
    return action;
  }

  @Override
  @NotNull
  public List<Event> raises() {
    final var list = new ArrayList<Event>();

    if (action instanceof RaiseAction) {
      list.add(((RaiseAction) action).getEvent());
    }

    return list;
  }

  public record Parameters(String name, Expression delay, Action action) {}
}
