package at.ac.uibk.dps.cirrina.execution.object.event;

import at.ac.uibk.dps.cirrina.csm.Csml.EventDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariableBuilder;
import at.ac.uibk.dps.cirrina.execution.object.expression.ExpressionBuilder;
import java.util.List;
import java.util.Map;

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

  private static List<ContextVariable> buildVariableList(Map<String, String> context) {
    return context
      .entrySet()
      .stream()
      .map(varEntry ->
        ContextVariableBuilder.empty()
          .name(varEntry.getKey()) // The key is the variable name
          .expression(ExpressionBuilder.from(varEntry.getValue()).build()) // The value is the expression
          .build()
      )
      .toList();
  }

  /**
   * Builds the event.
   *
   * @return The built event.
   */
  public Event build() {
    return new Event(
      eventDescription.getTopic(),
      eventDescription.getChannel(),
      buildVariableList(eventDescription.getData())
    );
  }
}
