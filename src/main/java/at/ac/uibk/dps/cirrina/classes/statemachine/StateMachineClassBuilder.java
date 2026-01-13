package at.ac.uibk.dps.cirrina.classes.statemachine;

import at.ac.uibk.dps.cirrina.classes.state.StateClassBuilder;
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClassBuilder;
import at.ac.uibk.dps.cirrina.csm.Csml.StateMachineDescription;
import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * StateClass machine builder. Builds a state machine based on a state machine class.
 */
public final class StateMachineClassBuilder {

  private final StateMachineDescription stateMachineDescription;

  private @Nullable String name;

  /**
   * Initializes this builder instance.
   *
   * @param stateMachineDescription state machine description
   */
  private StateMachineClassBuilder(StateMachineDescription stateMachineDescription) {
    this.stateMachineDescription = stateMachineDescription;
  }

  /**
   * Construct a builder from a state machine description.
   *
   * @param stateMachineDescription state machine description
   * @return this builder
   */
  public static StateMachineClassBuilder from(StateMachineDescription stateMachineDescription) {
    return new StateMachineClassBuilder(stateMachineDescription);
  }

  /**
   * Sets the name of the state machine.
   *
   * @param name name of the state machine
   * @return this builder
   */
  public StateMachineClassBuilder withName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Builds all nested state machines contained in the state machine class.
   *
   * @return a list containing all nested state machines
   * @throws IllegalArgumentException in case one nested state machine could not be built
   */
  private List<StateMachineClass> buildNestedStateMachines() throws IllegalArgumentException {
    // Build all nested state machines
    return stateMachineDescription
      .getStateMachines()
      .entrySet()
      .stream()
      .map(stateMachineEntry ->
        StateMachineClassBuilder.from(stateMachineEntry.getValue()) // Value is the state machine description
          .withName(stateMachineEntry.getKey()) // Key is the name of the state machine
          .build()
      )
      .toList();
  }

  /**
   * Builds a state machine which does not extend another state machine.
   *
   * @return the built state machine
   * @throws IllegalArgumentException in case the state machine could not be built
   */
  private StateMachineClass buildBase() throws IllegalArgumentException {
    var nestedStateMachines = buildNestedStateMachines();

    var parameters = new StateMachineClass.Parameters(
      name,
      stateMachineDescription.getLocal(),
      nestedStateMachines
    );

    var stateMachine = new StateMachineClass(parameters);

    // Attempt to add vertices
    stateMachineDescription
      .getStates()
      .entrySet()
      .stream()
      .map(stateEntry ->
        StateClassBuilder.from(stateMachine.getId(), stateEntry.getValue()) // Value is the state description
          .withName(stateEntry.getKey()) // Key is the name of the state
          .build()
      )
      .forEach(stateMachine::addVertex);

    return stateMachine;
  }

  /**
   * Builds the state machine.
   *
   * @return the state machine
   * @throws IllegalArgumentException if the state machine has declared a transition with an invalid (non-existent) target state
   * @throws IllegalArgumentException if the state machine has declared a transition between two states that is illegal
   * @throws IllegalArgumentException if the state machine has declared a state with a non-deterministic outward transition
   */
  public StateMachineClass build() throws IllegalArgumentException {
    var stateMachine = buildBase();

    // Attempt to add edges
    stateMachineDescription
      .getStates()
      .forEach((key, value) -> {
        // Acquire source node, this is expected to always succeed as we use the previously created state
        var sourceStateClass = stateMachine.findStateClassByName(key).get();

        BiConsumer<String, TransitionDescription> processTransitionEntry = (event, description) -> {
          var targetStateClass = Optional.ofNullable(description.getTo())
            .map(targetName ->
              stateMachine
                .findStateClassByName(targetName)
                .orElseThrow(() ->
                  new IllegalArgumentException(
                    "Transition '%s' has an invalid target state '%s'".formatted(name, targetName)
                  )
                )
            )
            .orElse(sourceStateClass);

          var builder = TransitionClassBuilder.from(description);

          if (event != null) {
            builder.withEvent(event);
          }

          if (!stateMachine.addEdge(sourceStateClass, targetStateClass, builder.build())) {
            throw new IllegalArgumentException(
              "The edge '%s' between '%s' and '%s' is illegal".formatted(
                name,
                sourceStateClass.getName(),
                targetStateClass.getName()
              )
            );
          }
        };

        value.getOn().forEach(processTransitionEntry);
        value.getAlways().forEach(desc -> processTransitionEntry.accept(null, desc));
      });

    return stateMachine;
  }
}
