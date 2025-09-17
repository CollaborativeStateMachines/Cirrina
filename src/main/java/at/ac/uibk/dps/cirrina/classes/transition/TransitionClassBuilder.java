package at.ac.uibk.dps.cirrina.classes.transition;

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.GuardDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.OnTransitionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription;
import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionBuilder;
import at.ac.uibk.dps.cirrina.execution.object.guard.Guard;
import at.ac.uibk.dps.cirrina.execution.object.guard.GuardBuilder;
import java.util.List;
import java.util.function.Function;

/**
 * Abstract transitionClass builder.
 */
public abstract class TransitionClassBuilder {

  /**
   * Construct a builder from a transition description.
   *
   * @param transitionDescription Transition description.
   * @return Builder.
   */
  public static TransitionClassBuilder from(TransitionDescription transitionDescription) {
    return new TransitionClassFromDescriptionBuilder(transitionDescription);
  }

  /**
   * Builds the transitionClass.
   *
   * @return the transitionClass.
   * @throws IllegalArgumentException In case the transitionClass could not be built.
   */
  public abstract TransitionClass build() throws IllegalArgumentException;

  /**
   * TransitionClass builder implementation. Builds a transitionClass based on a transitionClass class.
   *
   * @see StateMachineClassBuilder
   */
  private static final class TransitionClassFromDescriptionBuilder extends TransitionClassBuilder {

    /**
     * Transition description.
     */
    private final TransitionDescription transitionDescription;

    /**
     * Initializes this builder.
     *
     * @param transitionDescription Transition description.
     */
    private TransitionClassFromDescriptionBuilder(TransitionDescription transitionDescription) {
      this.transitionDescription = transitionDescription;
    }

    /**
     * Builds the transition class.
     *
     * @return Transition class.
     * @throws IllegalArgumentException If a guard name does not exist.
     * @throws IllegalArgumentException If an action name does not exist.
     */
    @Override
    public TransitionClass build() throws IllegalArgumentException {
      // Resolve guards
      Function<List<? extends GuardDescription>, List<Guard>> resolveGuards = (List<
        ? extends GuardDescription
      > guards) ->
        guards
          .stream()
          .map(guardClass -> GuardBuilder.from(guardClass).build())
          .toList();

      // Resolve actions
      Function<List<? extends ActionDescription>, List<Action>> resolveActions = (List<
        ? extends ActionDescription
      > actions) ->
        actions
          .stream()
          .map(actionClass -> ActionBuilder.from(actionClass).build())
          .toList();

      // Create the appropriate transitionClass
      switch (transitionDescription) {
        case OnTransitionDescription onTransitionClass -> {
          return new OnTransitionClass(
            onTransitionClass.getTarget(),
            transitionDescription.getElse(),
            resolveGuards.apply(onTransitionClass.getGuards()),
            resolveActions.apply(onTransitionClass.getActions()),
            onTransitionClass.getEvent()
          );
        }
        default -> {
          return new TransitionClass(
            transitionDescription.getTarget(),
            transitionDescription.getElse(),
            resolveGuards.apply(transitionDescription.getGuards()),
            resolveActions.apply(transitionDescription.getActions())
          );
        }
      }
    }
  }
}
