package at.ac.uibk.dps.cirrina.execution.object.event;

import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.ContextVariableDescription;
import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.EventDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariableBuilder;
import java.util.List;

/**
 * Event builder, used to build event objects.
 */
public class EventBuilder {

  /**
   * The event class to build from.
   */
  private final EventDescription eventDescription;

  /**
   * Initializes an event builder.
   *
   * @param eventDescription Event class.
   */
  private EventBuilder(EventDescription eventDescription) {
    this.eventDescription = eventDescription;
  }

  /**
   * Initializes an event builder.
   *
   * @param eventDescription Event class.
   * @return Event builder.
   */
  public static EventBuilder from(EventDescription eventDescription) {
    return new EventBuilder(eventDescription);
  }

  private static List<ContextVariable> buildVariableList(
    List<ContextVariableDescription> contextVariableDescriptions
  ) {
    return contextVariableDescriptions
      .stream()
      .map(c -> ContextVariableBuilder.from(c).build())
      .toList();
  }

  /**
   * Builds the event.
   *
   * @return The built event.
   */
  public Event build() {
    return new Event(
      eventDescription.getName(),
      eventDescription.getChannel(),
      buildVariableList(eventDescription.getData())
    );
  }
}
