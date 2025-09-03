package at.ac.uibk.dps.cirrina.classes.collaborativestatemachine;

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass;
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription;
import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.EventChannel;
import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextBuilder;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Collaborative state machine builder, builds a collaborative state machine class based on a collaborative state machine description.
 */
public final class CollaborativeStateMachineClassBuilder {

  /**
   * The collaborative state machine description.
   */
  private final CollaborativeStateMachineDescription collaborativeStateMachineDescription;

  /**
   * Initializes this collaborative state machine builder instance.
   *
   * @param collaborativeStateMachineDescription The collaborative state machine description.
   */
  private CollaborativeStateMachineClassBuilder(
    CollaborativeStateMachineDescription collaborativeStateMachineDescription
  ) {
    this.collaborativeStateMachineDescription = collaborativeStateMachineDescription;
  }

  /**
   * Construct a collaborative state machine builder from a description.
   *
   * @param collaborativeStateMachineDescription Collaborative state machine description.
   * @return Collaborative state machine builder.
   */
  public static CollaborativeStateMachineClassBuilder from(
    CollaborativeStateMachineDescription collaborativeStateMachineDescription
  ) {
    return new CollaborativeStateMachineClassBuilder(collaborativeStateMachineDescription);
  }

  /**
   * Builds the collaborative state machine class vertices.
   *
   * @param collaborativeStateMachineClass The collaborative state machine class being built.
   */
  private void buildVertices(CollaborativeStateMachineClass collaborativeStateMachineClass) {
    collaborativeStateMachineDescription
      .getStateMachines()
      .stream()
      .map(stateMachineClass -> StateMachineClassBuilder.from(stateMachineClass).build())
      .forEach(collaborativeStateMachineClass::addVertex);
  }

  /**
   * Builds the collaborative state machine class edges.
   *
   * @param collaborativeStateMachineClass The collaborative state machine class being built.
   */
  private void buildEdges(CollaborativeStateMachineClass collaborativeStateMachineClass) {
    Table<StateMachineClass, Event, List<StateMachineClass>> events = HashBasedTable.create();

    // Populate sources and targets
    populateSourceStateMachines(collaborativeStateMachineClass, events);
    populateTargetStateMachines(collaborativeStateMachineClass, events);

    // Add edges to the collaborative state machine graph
    addEdges(collaborativeStateMachineClass, events);
  }

  /**
   * Populates the source state machine classes according to pre-populated source state machine classes and raised events. The table is
   * expected to represent the source state machine class (row), raised event (column), and target state machine classes (cell).
   *
   * @param collaborativeStateMachineClass The collaborative state machine class being built.
   * @param events                         Raised events table.
   */
  private void populateSourceStateMachines(
    CollaborativeStateMachineClass collaborativeStateMachineClass,
    Table<StateMachineClass, Event, List<StateMachineClass>> events
  ) {
    // Add all source state machines and their raised events
    collaborativeStateMachineClass
      .vertexSet()
      .forEach(targetStateMachine ->
        targetStateMachine
          .getOutputEvents()
          .forEach(event -> events.put(targetStateMachine, event, new ArrayList<>()))
      );
  }

  /**
   * Populates the target state machine classes according to pre-populated source state machine classes and raised events. The table is
   * expected to represent the source state machine class (row), raised event (column), and target state machine classes (cell).
   *
   * @param collaborativeStateMachineClass The collaborative state machine class being built.
   * @param events                         Raised events table.
   */
  private void populateTargetStateMachines(
    CollaborativeStateMachineClass collaborativeStateMachineClass,
    Table<StateMachineClass, Event, List<StateMachineClass>> events
  ) {
    for (final var entry : events.cellSet()) {
      final var sourceStateMachineClass = entry.getRowKey();
      final var raisedEvent = entry.getColumnKey();
      final var targetStateMachineClasses = entry.getValue();

      // Determine if the potential target state machine raises the column's raised event and append the target state
      // machine to the cell if it does
      for (final var targetStateMachine : collaborativeStateMachineClass.vertexSet()) {
        final var handledEvents = targetStateMachine.getInputEvents();

        // If the raised event is internal, the source- and target state machines need to match, otherwise it can be
        // added to the table. At this point we assume that external events will be bound, we cannot resolve the case
        // that it does not here
        if (
          ((raisedEvent.getChannel() == EventChannel.INTERNAL &&
              sourceStateMachineClass == targetStateMachine) ||
            raisedEvent.getChannel() == EventChannel.GLOBAL ||
            raisedEvent.getChannel() == EventChannel.EXTERNAL) &&
          handledEvents.contains(raisedEvent.getName())
        ) {
          targetStateMachineClasses.add(targetStateMachine);
        }
      }
    }
  }

  /**
   * Add edges to the currently building collaborative state machine class according to the provided table. The table is expected to
   * represent the source state machine class (row), raised event (column), and target state machines classes (cell).
   *
   * @param collaborativeStateMachineClass The collaborative state machine class being built.
   * @param events                         Raised events table.
   */
  private void addEdges(
    CollaborativeStateMachineClass collaborativeStateMachineClass,
    Table<StateMachineClass, Event, List<StateMachineClass>> events
  ) {
    // Process the events table and add all edges
    for (final var rowEntry : events.rowMap().entrySet()) {
      final var sourceStateMachineClass = rowEntry.getKey();

      for (var colEntry : rowEntry.getValue().entrySet()) {
        final var raisedEvent = colEntry.getKey();

        for (final var targetStateMachineClass : colEntry.getValue()) {
          collaborativeStateMachineClass.addEdge(
            sourceStateMachineClass,
            targetStateMachineClass,
            raisedEvent
          );
        }
      }
    }
  }

  /**
   * Builds the collaborative state machine class.
   *
   * @return Built collaborative state machine class.
   * @throws IllegalArgumentException If the collaborative state machine could not be built.
   */
  public CollaborativeStateMachineClass build() throws IllegalArgumentException {
    // Construct a local context from the persistent context description, such that we can easily acquire the context variables
    Context persistentContext;
    try {
      persistentContext = ContextBuilder.from(
        collaborativeStateMachineDescription.getPersistentContext()
      )
        .inMemoryContext(true)
        .build();
    } catch (IOException ignored) {
      throw new IllegalStateException();
    }

    try {
      final var collaborativeStateMachine = new CollaborativeStateMachineClass(
        collaborativeStateMachineDescription.getName(),
        persistentContext != null ? persistentContext.getAll() : List.of()
      );

      buildVertices(collaborativeStateMachine);
      buildEdges(collaborativeStateMachine);

      return collaborativeStateMachine;
    } catch (IOException ignored) {
      throw new IllegalStateException();
    }
  }
}
