package at.ac.uibk.dps.cirrina.classes.statemachine;

import at.ac.uibk.dps.cirrina.classes.state.StateClass;
import at.ac.uibk.dps.cirrina.classes.transition.OnTransitionClass;
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass;
import at.ac.uibk.dps.cirrina.csml.description.Csml.ContextDescription;
import at.ac.uibk.dps.cirrina.execution.object.action.EventRaisingAction;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.io.plantuml.Exportable;
import at.ac.uibk.dps.cirrina.io.plantuml.PlantUmlVisitor;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jgrapht.graph.DirectedPseudograph;

/**
 * State machine class, represents the structure of a state machine.
 * <p>
 * A state machine is a graph consisting of state classes as vertices and transition classes as edges. An edge between two vertices (states)
 * represents a possible transition between the states.
 */
public final class StateMachineClass
  extends DirectedPseudograph<StateClass, TransitionClass>
  implements Exportable {

  /**
   * The state machine class ID.
   */
  private final UUID id = UUID.randomUUID();

  /**
   * The collection of nested state machine classes.
   */
  private final List<StateMachineClass> nestedStateMachineClasses;

  /**
   * The name.
   */
  private final String name;

  /**
   * The local context class, can be null in case no local context has been declared.
   */
  private final @Nullable ContextDescription localContextClass;

  /**
   * Initializes this state machine class instance.
   *
   * @param parameters Parameters.
   */
  StateMachineClass(Parameters parameters) {
    super(TransitionClass.class);
    this.name = parameters.name;
    this.localContextClass = parameters.localContextClass;
    this.nestedStateMachineClasses = Collections.unmodifiableList(
      parameters.nestedStateMachineClasses
    );
  }

  /**
   * Return a string representation.
   *
   * @return String representation.
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * PlantUML visitor accept.
   *
   * @param visitor PlantUML visitor.
   */
  @Override
  public void accept(PlantUmlVisitor visitor) {
    visitor.visit(this);
  }

  /**
   * Returns a state by its name. If not one state is known with the supplied name, empty is returned.
   *
   * @param stateName Name of the state to return.
   * @return The state with the supplied name or empty.
   * @throws IllegalArgumentException In case multiple states were found for the supplied name.
   */
  public Optional<StateClass> findStateClassByName(String stateName) {
    // Attempt to match the provided name to a known state
    final var states = vertexSet()
      .stream()
      .filter(state -> state.getName().equals(stateName))
      .toList();

    if (states.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(states.getFirst());
  }

  /**
   * Returns the transitions from a state that are triggered by a given event name.
   *
   * @param fromStateClass From state.
   * @param eventName      The event name.
   * @return The list of on-transitions.
   */
  public List<OnTransitionClass> findOnTransitionsFromStateByEventName(
    StateClass fromStateClass,
    String eventName
  ) {
    return outgoingEdgesOf(fromStateClass)
      .stream()
      .filter(OnTransitionClass.class::isInstance)
      .map(OnTransitionClass.class::cast)
      .filter(transition -> transition.getEventName().equals(eventName))
      .toList();
  }

  /**
   * Returns the transitions from a state that are not event-triggered.
   *
   * @param fromStateClass From state.
   * @return The list of always-transitions.
   */
  public List<TransitionClass> findAlwaysTransitionsFromState(StateClass fromStateClass) {
    return outgoingEdgesOf(fromStateClass)
      .stream()
      .filter(transition -> !(transition instanceof OnTransitionClass))
      .toList();
  }

  /**
   * Returns the collection of nested state machine classes.
   *
   * @return Nested state machine classes.
   */
  public List<StateMachineClass> getNestedStateMachineClasses() {
    return nestedStateMachineClasses;
  }

  /**
   * Returns the ID.
   *
   * @return ID.
   */
  public UUID getId() {
    return id;
  }

  /**
   * Returns the name.
   *
   * @return Name.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the local context class or empty.
   *
   * @return Local context class or empty.
   */
  public Optional<ContextDescription> getLocalContextClass() {
    return Optional.ofNullable(localContextClass);
  }

  /**
   * Returns the initial state of this state machine.
   *
   * @return Initial state.
   */
  public StateClass getInitialState() {
    return vertexSet().stream().filter(StateClass::isInitial).findFirst().get();
  }

  /**
   * Returns the collection of events handled by this state machine.
   *
   * @return Events handled by this state machine.
   */
  public List<String> getInputEvents() {
    return edgeSet()
      .stream()
      .filter(OnTransitionClass.class::isInstance)
      .map(onTransition -> ((OnTransitionClass) onTransition).getEventName())
      .toList();
  }

  /**
   * Returns the events that may be raised from this state machine.
   *
   * @return Output events.
   */
  public List<Event> getOutputEvents() {
    return Stream.concat(
      vertexSet()
        .stream()
        .flatMap(v -> v.getActionsOfType(EventRaisingAction.class).stream()),
      edgeSet()
        .stream()
        .flatMap(e -> e.getActionsOfType(EventRaisingAction.class).stream())
    )
      .flatMap(action -> action.raises().stream())
      .toList();
  }

  /**
   * Parameters for the construction of a state machine class.
   *
   * @param name                      Name.
   * @param localContextClass         Local context class or empty if none declared.
   * @param nestedStateMachineClasses Nested state machine classes.
   */
  record Parameters(
    String name,
    @Nullable ContextDescription localContextClass,
    List<StateMachineClass> nestedStateMachineClasses
  ) {}
}
