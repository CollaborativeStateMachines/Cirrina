package at.ac.uibk.dps.cirrina.classes.collaborativestatemachine;

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import java.util.List;
import java.util.Optional;
import org.jgrapht.graph.DirectedPseudograph;

/**
 * Collaborative state machine class, describes the structure of a collaborative state machine.
 * <p>
 * A collaborative state machine is a graph with state machine classes as vertices and events as edges. An edge in the collaborative state
 * machine graphs represents how a state machine can be "activated" by another state machine based on an event.
 */
public final class CollaborativeStateMachineClass
  extends DirectedPseudograph<StateMachineClass, Event> {

  /**
   * The name of the collaborative state machine.
   */
  private final String name;

  /**
   * The collection of persistent context variables.
   */
  private final List<ContextVariable> persistentContextVariables;

  /**
   * Initializes this collaborative state machine class.
   *
   * @param name Name.
   */
  CollaborativeStateMachineClass(String name, List<ContextVariable> persistentContextVariables) {
    super(Event.class);
    this.name = name;
    this.persistentContextVariables = persistentContextVariables;
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
   * Returns a state machine class by its name.
   * <p>
   * If no state machine class is known with the supplied name, empty is returned.
   *
   * @param name Name of the state machine to return.
   * @return The state machine with the supplied name or empty.
   */
  public Optional<StateMachineClass> findStateMachineClassByName(String name) {
    // Attempt to match the provided name to a known state machine
    var states = vertexSet()
      .stream()
      .filter(state -> state.getName().equals(name))
      .toList();

    // Expect precisely one state machine with the provided name
    if (states.size() != 1) {
      return Optional.empty();
    }

    return Optional.of(states.getFirst());
  }

  /**
   * Returns the collection of state machine classes.
   *
   * @return State machine classes.
   */
  public List<StateMachineClass> getStateMachineClasses() {
    return List.copyOf(vertexSet());
  }

  /**
   * Returns the collection of persistent context variables.
   *
   * @return Persistent context variables.
   */
  public List<ContextVariable> getPersistentContextVariables() {
    return persistentContextVariables;
  }
}
