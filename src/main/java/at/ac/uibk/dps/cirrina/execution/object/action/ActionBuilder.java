package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.CaseDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.EvalDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.EventDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.InvokeDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.MatchDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.RaiseDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.TimeoutDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.TimeoutResetDescription;
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
   * Returns a map of cases.
   *
   * @param cases match case descriptions
   * @return cases
   * @throws IllegalArgumentException if an action name does not exist
   */
  private Map<Expression, Action> buildCases(List<CaseDescription> cases) {
    final Map<Expression, Action> ret = new HashMap<>();

    for (final var _case : cases) {
      ret.put(
        ExpressionBuilder.from(_case.getOf()).build(),
        ActionBuilder.from(_case.getThen()).build()
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
      case EvalDescription eval -> {
        // Acquire the expression
        final var expression = ExpressionBuilder.from(eval.getExpression()).build();

        // Construct parameters
        final var parameters = new EvalAction.Parameters(expression);

        // Construct the assign action
        return new EvalAction(parameters);
      }
      case InvokeDescription invoke -> {
        // Acquire the input variables
        final var input = buildVariableList(invoke.getInput());

        // Acquire the done events
        final var done = buildEvents(invoke.getRaises());

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
      case MatchDescription match -> {
        // Acquire the value expression
        final var valueExpression = ExpressionBuilder.from(match.getValue()).build();

        // Acquire the cases
        final var cases = buildCases(match.getCases());

        // Construct parameters
        final var parameters = new MatchAction.Parameters(valueExpression, cases);

        // Construct the match action
        return new MatchAction(parameters);
      }
      case RaiseDescription raise -> {
        // Acquire the event
        final var event = EventBuilder.from(raise.getEvent()).build();

        // Construct parameters
        final var parameters = new RaiseAction.Parameters(event);

        // Construct the raise action
        return new RaiseAction(parameters);
      }
      case TimeoutDescription timeout -> {
        // Acquire the action name, for timeout actions, the name is always required
        final var name = Optional.ofNullable(timeout.getName()).orElseThrow(() ->
          new IllegalArgumentException("Timeout action name is not provided")
        );

        // Acquire the delay expression
        final var delayExpression = ExpressionBuilder.from(timeout.getDelay()).build();

        // Acquire the timeout action
        final var timeoutAction = ActionBuilder.from(timeout.getDo()).build();

        // Construct parameters
        final var parameters = new TimeoutAction.Parameters(name, delayExpression, timeoutAction);

        // Construct the timeout action
        return new TimeoutAction(parameters);
      }
      case TimeoutResetDescription timeoutReset -> {
        // Construct parameters
        final var parameters = new TimeoutResetAction.Parameters(timeoutReset.getName());

        // Construct the timeout reset action
        return new TimeoutResetAction(parameters);
      }
      default -> throw new UnsupportedOperationException(
        "Action type '%s' is not known".formatted(actionDescription.getType())
      );
    }
  }
}
