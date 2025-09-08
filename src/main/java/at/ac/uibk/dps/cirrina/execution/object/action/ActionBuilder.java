package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.csml.description.Csml.ActionDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.AssignActionDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.ContextVariableDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.CreateActionDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.EventDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.InvokeActionDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.MatchActionDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.MatchCaseDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.RaiseActionDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.TimeoutActionDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.TimeoutResetActionDescription;
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

  /**
   * The action class to build from.
   */
  private final ActionDescription actionDescription;

  /**
   * Initializes an action builder.
   *
   * @param actionDescription Action class.
   */
  private ActionBuilder(ActionDescription actionDescription) {
    this.actionDescription = actionDescription;
  }

  /**
   * Creates an action builder.
   *
   * @param actionClass Action class.
   * @return Action builder.
   */
  public static ActionBuilder from(ActionDescription actionClass) {
    return new ActionBuilder(actionClass);
  }

  /**
   * Returns a list of variables.
   *
   * @param contextVariableDescriptions Variable classes.
   * @return Variables.
   */
  private static List<ContextVariable> buildVariableList(
    List<ContextVariableDescription> contextVariableDescriptions
  ) {
    return contextVariableDescriptions
      .stream()
      .map(c -> ContextVariableBuilder.from(c).build())
      .toList();
  }

  /**
   * Returns a list of events.
   *
   * @param eventDescriptions Event classes.
   * @return Events.
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
   * @param cases Case classes.
   * @return Cases.
   * @throws IllegalArgumentException If an action name does not exist.
   */
  private Map<Expression, Action> buildCases(List<MatchCaseDescription> cases) {
    final Map<Expression, Action> ret = new HashMap<>();

    for (final var casee : cases) {
      ret.put(
        ExpressionBuilder.from(casee.getCase()).build(),
        ActionBuilder.from(casee.getAction()).build()
      );
    }

    return ret;
  }

  /**
   * Builds the action.
   *
   * @return The built action.
   * @throws IllegalArgumentException      If an action name does not exist.
   * @throws UnsupportedOperationException If an action is of an unknown type.
   */
  public Action build() throws IllegalArgumentException, IllegalStateException {
    switch (actionDescription) {
      case AssignActionDescription assign -> {
        // Acquire the context variable
        final var contextVariable = ContextVariableBuilder.from(assign.getVariable()).build();

        // Construct parameters
        final var parameters = new AssignAction.Parameters(contextVariable);

        // Construct the assign action
        return new AssignAction(parameters);
      }
      case CreateActionDescription create -> {
        final var contextVariable = ContextVariableBuilder.from(create.getVariable()).build();

        // Construct parameters
        final var parameters = new CreateAction.Parameters(
          contextVariable,
          create.isIsPersistent()
        );

        // Construct the create action
        return new CreateAction(parameters);
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
          done,
          invoke.getOutput()
        );

        // Construct the invoke action
        return new InvokeAction(parameters);
      }
      case MatchActionDescription match -> {
        // Acquire the value expression
        final var valueExpression = ExpressionBuilder.from(match.getValue()).build();

        // Acquire the cases
        final var cases = buildCases(match.getCases());

        // Construct parameters
        final var parameters = new MatchAction.Parameters(valueExpression, cases);

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
