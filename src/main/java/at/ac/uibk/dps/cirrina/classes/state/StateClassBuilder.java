package at.ac.uibk.dps.cirrina.classes.state;

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription;
import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionBuilder;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

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

  public StateClass build() throws IllegalArgumentException {
    final BiFunction<ActionDescription, String, Action> resolveAction = (desc, name) -> {
      final var builder = ActionBuilder.from(desc);
      if (name != null && !name.isBlank()) {
        builder.withName(name);
      }
      return builder.build();
    };

    final var entryActions = stateDescription
      .getEntry()
      .stream()
      .map(desc -> resolveAction.apply(desc, null))
      .toList();

    final var exitActions = stateDescription
      .getExit()
      .stream()
      .map(desc -> resolveAction.apply(desc, null))
      .toList();

    final var whileActions = stateDescription
      .getWhile()
      .stream()
      .map(desc -> resolveAction.apply(desc, null))
      .toList();

    final List<Action> afterActions = new ArrayList<>();
    stateDescription
      .getAfter()
      .forEach((name, desc) -> afterActions.add(resolveAction.apply(desc, name)));

    if (!afterActions.stream().allMatch(TimeoutAction.class::isInstance)) {
      throw new IllegalArgumentException("After action is not a timeout action");
    }

    return new StateClass(
      new StateClass.BaseParameters(
        parentStateMachineId,
        name,
        stateDescription.getLocal(),
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
