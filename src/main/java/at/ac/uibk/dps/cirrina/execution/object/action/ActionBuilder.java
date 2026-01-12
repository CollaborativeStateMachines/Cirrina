package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.EvalActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.EventDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.InvokeActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.MatchActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.MatchArmDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.RaiseActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.TimeoutActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.TimeoutResetActionDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariableBuilder;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.event.EventBuilder;
import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;
import at.ac.uibk.dps.cirrina.execution.object.expression.ExpressionBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Action builder, used to build action objects.
 */
public final class ActionBuilder {

  private final ActionDescription actionDescription;

  /**
   * Initializes an action builder.
   *
   * @param actionDescription action description
   */
  private ActionBuilder(ActionDescription actionDescription) {
    this.actionDescription = actionDescription;
  }

  /**
   * Creates an action builder.
   *
   * @param actionClass action class
   * @return action builder
   */
  public static ActionBuilder from(ActionDescription actionClass) {
    return new ActionBuilder(actionClass);
  }

  /**
   * Returns a list of variables.
   *
   * @param context context
   * @return variables
   */
  private static List<ContextVariable> buildVariableList(Map<String, String> context) {
    return context
      .entrySet()
      .stream()
      .map(varEntry ->
        ContextVariableBuilder.empty()
          .name(varEntry.getKey())
          .expression(ExpressionBuilder.from(varEntry.getValue()).build())
          .build()
      )
      .toList();
  }

  /**
   * Returns a list of events.
   *
   * @param eventDescriptions event descriptions
   * @return events
   */
  private static List<Event> buildEvents(List<EventDescription> eventDescriptions) {
    return eventDescriptions
      .stream()
      .map(e -> EventBuilder.from(e).build())
      .toList();
  }

  /**
   * Returns a map of arms.
   *
   * @param arms match arm descriptions
   * @return arms
   * @throws IllegalArgumentException if an action name does not exist
   */
  private Map<Expression, Action> buildArms(List<MatchArmDescription> arms) {
    final Map<Expression, Action> ret = new HashMap<>();

    for (final var arm : arms) {
      ret.put(
        ExpressionBuilder.from(arm.getOf()).build(),
        ActionBuilder.from(arm.getAction()).build()
      );
    }

    return ret;
  }

  /**
   * Builds the action.
   *
   * @return the built action
   * @throws IllegalArgumentException      if an action name does not exist
   * @throws UnsupportedOperationException if an action is of an unknown type
   */
  public Action build() throws IllegalArgumentException, IllegalStateException {
    switch (actionDescription) {
      case EvalActionDescription eval -> {
        // Acquire the expression
        final var expression = ExpressionBuilder.from(eval.getExpression()).build();

        // Construct parameters
        final var parameters = new EvalAction.Parameters(expression);

        // Construct the assign action
        return new EvalAction(parameters);
      }
      case InvokeActionDescription invoke -> {
        // Acquire the input variables
        final var input = buildVariableList(invoke.getInput());

        // Acquire the done events
        final var done = buildEvents(invoke.getDone());

        // Construct parameters
        final var parameters = new InvokeAction.Parameters(
          invoke.getServiceType(),
          invoke.isIsLocal(),
          input,
          done
        );

        // Construct the invoke action
        return new InvokeAction(parameters);
      }
      case MatchActionDescription match -> {
        // Acquire the value expression
        final var valueExpression = ExpressionBuilder.from(match.getValue()).build();

        // Acquire the arms
        final var arms = buildArms(match.getArms());

        // Construct parameters
        final var parameters = new MatchAction.Parameters(valueExpression, arms);

        // Construct the match action
        return new MatchAction(parameters);
      }
      case RaiseActionDescription raise -> {
        // Acquire the event
        final var event = EventBuilder.from(raise.getEvent()).build();

        // Construct parameters
        final var parameters = new RaiseAction.Parameters(event);

        // Construct the raise action
        return new RaiseAction(parameters);
      }
      case TimeoutActionDescription timeout -> {
        // Acquire the action name, for timeout actions, the name is always required
        final var name = Optional.ofNullable(timeout.getName()).orElseThrow(() ->
          new IllegalArgumentException("Timeout action name is not provided")
        );

        // Acquire the delay expression
        final var delayExpression = ExpressionBuilder.from(timeout.getDelay()).build();

        // Acquire the timeout action
        final var timeoutAction = ActionBuilder.from(timeout.getAction()).build();

        // Construct parameters
        final var parameters = new TimeoutAction.Parameters(name, delayExpression, timeoutAction);

        // Construct the timeout action
        return new TimeoutAction(parameters);
      }
      case TimeoutResetActionDescription timeoutReset -> {
        // Construct parameters
        final var parameters = new TimeoutResetAction.Parameters(timeoutReset.getAction());

        // Construct the timeout reset action
        return new TimeoutResetAction(parameters);
      }
      default -> throw new UnsupportedOperationException(
        "Action type '%s' is not known".formatted(actionDescription.getType())
      );
    }
  }
}
