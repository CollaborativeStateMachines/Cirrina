package at.ac.uibk.dps.cirrina.classes.transition;

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription;
import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionBuilder;
import at.ac.uibk.dps.cirrina.execution.object.guard.Guard;
import at.ac.uibk.dps.cirrina.execution.object.guard.GuardBuilder;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * Abstract transitionClass builder.
 */
public class TransitionClassBuilder {

  /**
   * Transition description.
   */
  private final TransitionDescription transitionDescription;

  private @Nullable String event;

  /**
   * Initializes this builder.
   *
   * @param transitionDescription Transition description.
   */
  private TransitionClassBuilder(TransitionDescription transitionDescription) {
    this.transitionDescription = transitionDescription;
  }

  /**
   * Construct a builder from a transition description.
   *
   * @param transitionDescription Transition description.
   * @return Builder.
   */
  public static TransitionClassBuilder from(TransitionDescription transitionDescription) {
    return new TransitionClassBuilder(transitionDescription);
  }

  public TransitionClassBuilder withEvent(String event) {
    this.event = event;
    return this;
  }

  /**
   * Builds the transition class.
   *
   * @return Transition class.
   * @throws IllegalArgumentException If a guard name does not exist.
   * @throws IllegalArgumentException If an action name does not exist.
   */
  public TransitionClass build() throws IllegalArgumentException {
    // Resolve guards
    Function<List<String>, List<Guard>> resolveGuards = (List<String> guards) ->
      guards
        .stream()
        .map(expression -> GuardBuilder.from(expression).build())
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
    // KLUDGE: The CSML language used to have both on-transitions and transitions in the language
    // itself, we simplified this, but the OnTransitionClass and TransitionClass remain to make
    // this a minimal change at the time of writing
    // TODO: Unify OnTransitionClass and TransitionClass
    if (event != null) {
      return new OnTransitionClass(
        transitionDescription.getTo(),
        transitionDescription.getOr(),
        resolveGuards.apply(transitionDescription.getIif()),
        resolveActions.apply(transitionDescription.getDo()),
        event
      );
    } else {
      return new TransitionClass(
        transitionDescription.getTo(),
        transitionDescription.getOr(),
        resolveGuards.apply(transitionDescription.getIif()),
        resolveActions.apply(transitionDescription.getDo())
      );
    }
  }
}
