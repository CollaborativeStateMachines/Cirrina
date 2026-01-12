package at.ac.uibk.dps.cirrina.classes.statemachine;

import at.ac.uibk.dps.cirrina.classes.state.StateClass;
import at.ac.uibk.dps.cirrina.classes.transition.OnTransitionClass;
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass;
import at.ac.uibk.dps.cirrina.execution.object.action.EventRaisingAction;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.io.plantuml.Exportable;
import at.ac.uibk.dps.cirrina.io.plantuml.PlantUmlVisitor;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

  private final UUID id = UUID.randomUUID();

  private final List<StateMachineClass> nestedStateMachineClasses;

  private final String name;

  private final @Nullable Map<String, String> localContextDescription;

  /**
   * Initializes this state machine class instance.
   *
   * @param parameters parameters
   */
  StateMachineClass(Parameters parameters) {
    super(TransitionClass.class);
    this.name = parameters.name;
    this.localContextDescription = parameters.localContextDescription;
    this.nestedStateMachineClasses = Collections.unmodifiableList(
      parameters.nestedStateMachineClasses
    );
  }

  /**
   * Return a string representation.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * PlantUML visitor accept.
   *
   * @param visitor PlantUML visitor
   */
  @Override
  public void accept(PlantUmlVisitor visitor) {
    visitor.visit(this);
  }

  /**
   * Returns a state by its name. If not one state is known with the supplied name, empty is returned.
   *
   * @param stateName name of the state to return
   * @return the state with the supplied name or empty
   * @throws IllegalArgumentException in case multiple states were found for the supplied name
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
   * @param fromStateClass from state
   * @param eventName      the event name
   * @return the list of on-transitions
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
   * @param fromStateClass from state
   * @return the list of always-transitions
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
   * @return nested state machine classes
   */
  public List<StateMachineClass> getNestedStateMachineClasses() {
    return nestedStateMachineClasses;
  }

  /**
   * Returns the ID.
   *
   * @return ID
   */
  public UUID getId() {
    return id;
  }

  /**
   * Returns the name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the local context class or empty.
   *
   * @return local context class or empty
   */
  public Optional<Map<String, String>> getLocalContextDescription() {
    return Optional.ofNullable(localContextDescription);
  }

  /**
   * Returns the initial state of this state machine.
   *
   * @return initial state
   */
  public StateClass getInitialState() {
    return vertexSet().stream().filter(StateClass::isInitial).findFirst().get();
  }

  /**
   * Returns the collection of events handled by this state machine.
   *
   * @return events handled by this state machine
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
   * @return output events
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
   * @param name                      name
   * @param localContextDescription   local context description or empty if none declared
   * @param nestedStateMachineClasses nested state machine classes
   */
  record Parameters(
    String name,
    @Nullable Map<String, String> localContextDescription,
    List<StateMachineClass> nestedStateMachineClasses
  ) {}
}
