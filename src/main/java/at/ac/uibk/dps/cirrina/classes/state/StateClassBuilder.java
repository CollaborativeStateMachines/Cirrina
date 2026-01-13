package at.ac.uibk.dps.cirrina.classes.state;

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription;
import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionBuilder;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class StateClassBuilder {

  private final UUID parentStateMachineId;

  private final StateDescription stateDescription;

  private @Nullable String name;

  private StateClassBuilder(UUID parentStateMachineId, StateDescription stateDescription) {
    this.parentStateMachineId = parentStateMachineId;
    this.stateDescription = stateDescription;
  }

  /**
   * Construct a state class builder from a state description without a base state class.
   *
   * @param parentStateMachineId This must be removed.
   * @param stateDescription     State description.
   * @return State class builder.
   */
  public static StateClassBuilder from(
    UUID parentStateMachineId,
    StateDescription stateDescription
  ) {
    return new StateClassBuilder(parentStateMachineId, stateDescription);
  }

  public StateClassBuilder withName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Builds the state class.
   *
   * @return State class.
   * @throws IllegalArgumentException If an action name does not exist.
   * @throws IllegalArgumentException If an after action is not a timeout action.
   */
  public StateClass build() throws IllegalArgumentException {
    // Resolve actions
    final Function<List<? extends ActionDescription>, List<Action>> resolveActions = (List<
      ? extends ActionDescription
    > actions) ->
      actions
        .stream()
        .map(actionDescription -> ActionBuilder.from(actionDescription).build())
        .toList();

    final var entryActions = resolveActions.apply(stateDescription.getEntry());
    final var exitActions = resolveActions.apply(stateDescription.getExit());
    final var whileActions = resolveActions.apply(stateDescription.getWhile());
    final var afterActions = resolveActions.apply(stateDescription.getAfter());

    if (!afterActions.stream().allMatch(TimeoutAction.class::isInstance)) {
      throw new IllegalArgumentException("After action is not a timeout action");
    }

    // Create the state class
    return new StateClass(
      new StateClass.BaseParameters(
        parentStateMachineId,
        name,
        stateDescription.getLocalContext(),
        stateDescription.isInitial(),
        stateDescription.isTerminal(),
        entryActions,
        exitActions,
        whileActions,
        afterActions
      )
    );
  }
}
