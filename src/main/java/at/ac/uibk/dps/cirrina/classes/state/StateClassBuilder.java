package at.ac.uibk.dps.cirrina.classes.state;

import at.ac.uibk.dps.cirrina.csml.description.Csml.ActionDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.StateDescription;
import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionBuilder;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Abstract stateClass builder
 */
public abstract class StateClassBuilder {

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
    return new StateClassFromDescriptionBuilder(parentStateMachineId, stateDescription);
  }

  /**
   * Builds the stateClass.
   *
   * @return the stateClass.
   * @throws IllegalArgumentException In case the stateClass could not be built.
   */
  public abstract StateClass build() throws IllegalArgumentException;

  /**
   * StateClass builder implementation. Builds a stateClass based on a stateClass class.
   */
  private static final class StateClassFromDescriptionBuilder extends StateClassBuilder {

    /**
     * ID of the parent state machine class.
     */
    private final UUID parentStateMachineId;

    /**
     * State description.
     */
    private final StateDescription stateDescription;

    /**
     * Initializes this builder instance for a base state class.
     *
     * @param parentStateMachineId ID of the parent state machine class.
     * @param stateDescription     State description.
     */
    private StateClassFromDescriptionBuilder(
      UUID parentStateMachineId,
      StateDescription stateDescription
    ) {
      this.parentStateMachineId = parentStateMachineId;
      this.stateDescription = stateDescription;
    }

    /**
     * Builds the state class.
     *
     * @return State class.
     * @throws IllegalArgumentException If an action name does not exist.
     * @throws IllegalArgumentException If an after action is not a timeout action.
     */
    @Override
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
          stateDescription.getName(),
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
}
