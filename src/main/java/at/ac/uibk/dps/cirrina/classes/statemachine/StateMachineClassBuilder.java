package at.ac.uibk.dps.cirrina.classes.statemachine;

import at.ac.uibk.dps.cirrina.classes.state.StateClassBuilder;
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClassBuilder;
import at.ac.uibk.dps.cirrina.csml.description.Csml.StateDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.StateMachineDescription;
import at.ac.uibk.dps.cirrina.csml.description.Csml.TransitionDescription;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * StateClass machine builder. Builds a state machine based on a state machine class.
 */
public final class StateMachineClassBuilder {

  /**
   * State machine description.
   */
  private final StateMachineDescription stateMachineDescription;

  /**
   * Initializes this builder instance.
   *
   * @param stateMachineDescription  State machine description.
   */
  private StateMachineClassBuilder(StateMachineDescription stateMachineDescription) {
    this.stateMachineDescription = stateMachineDescription;
  }

  /**
   * Construct a builder from a state machine description.
   *
   * @param stateMachineDescription State machine description.
   * @return Builder.
   */
  public static StateMachineClassBuilder from(StateMachineDescription stateMachineDescription) {
    return new StateMachineClassBuilder(stateMachineDescription);
  }

  /**
   * Builds all nested state machines contained in the state machine class.
   *
   * @return A list containing all nested state machines.
   * @throws IllegalArgumentException In case one nested state machine could not be built.
   */
  private List<StateMachineClass> buildNestedStateMachines() throws IllegalArgumentException {
    // Build all nested state machines
    return stateMachineDescription
      .getStateMachines()
      .stream()
      .map(nestedStateMachineClass -> new StateMachineClassBuilder(nestedStateMachineClass).build())
      .toList();
  }

  /**
   * Builds a state machine which does not extend another state machine.
   *
   * @return The state machine.
   * @throws IllegalArgumentException In case the state machine could not be built.
   */
  private StateMachineClass buildBase() throws IllegalArgumentException {
    var nestedStateMachines = buildNestedStateMachines();

    var parameters = new StateMachineClass.Parameters(
      stateMachineDescription.getName(),
      stateMachineDescription.getLocalContext(),
      nestedStateMachines
    );

    var stateMachine = new StateMachineClass(parameters);

    // Attempt to add vertices
    stateMachineDescription
      .getStates()
      .stream()
      .map(stateClass -> StateClassBuilder.from(stateMachine.getId(), stateClass).build())
      .forEach(stateMachine::addVertex);

    return stateMachine;
  }

  /**
   * Builds the state machine.
   *
   * @return The state machine.
   * @throws IllegalArgumentException If the state machine has declared a transition with an invalid (non-existent) target state.
   * @throws IllegalArgumentException If the state machine has declared a transition between two states that is illegal.
   * @throws IllegalArgumentException If the state machine has declared a state with a non-deterministic outward transition.
   */
  public StateMachineClass build() throws IllegalArgumentException {
    var stateMachine = buildBase();

    // Attempt to add edges
    stateMachineDescription
      .getStates()
      .stream()
      .map(StateDescription.class::cast)
      .forEach(stateClass -> {
        // Acquire source node, this is expected to always succeed as we use the previously created state
        var sourceStateClass = stateMachine.findStateClassByName(stateClass.getName()).get();

        Consumer<List<? extends TransitionDescription>> processTransitions = on -> {
          for (var transitionClass : on) {
            // Acquire the target node, if the target is not provided, this is a self-transition
            var targetStateClass = Optional.ofNullable(transitionClass.getTarget())
              .map(targetName ->
                stateMachine
                  .findStateClassByName(targetName)
                  .orElseThrow(() ->
                    new IllegalArgumentException(
                      "Transition has an invalid target state '%s'".formatted(targetName)
                    )
                  )
              )
              .orElse(sourceStateClass);

            // Attempt to add an edge to the state machine graph that resembles the transition
            if (
              !stateMachine.addEdge(
                sourceStateClass,
                targetStateClass,
                TransitionClassBuilder.from(transitionClass).build()
              )
            ) {
              throw new IllegalArgumentException(
                "The edge between states '%s' and '%s' is illegal in '%s'".formatted(
                  sourceStateClass.getName(),
                  targetStateClass.getName(),
                  stateMachineDescription.getName()
                )
              );
            }
          }
        };

        // TODO: This is actually allowed, depending on the guard conditions
        /* // Ensure that "on" transitions have distinct events
          var hasDuplicateEdges = stateClass.getOn().stream()
              .collect(Collectors.groupingBy(OnTransitionDescription::getEvent, Collectors.counting())).entrySet().stream()
              .anyMatch(entry -> entry.getValue() > 1);
          if (hasDuplicateEdges) {
            throw new IllegalArgumentException(
                "Multiple outwards transitions with the same event in '%s'".formatted(stateMachineDescription.name));
          }*/

        processTransitions.accept(stateClass.getOn());

        // Attempt to add edges corresponding to the "always" transitions, these transitions are optional
        processTransitions.accept(stateClass.getAlways());
      });

    return stateMachine;
  }
}
