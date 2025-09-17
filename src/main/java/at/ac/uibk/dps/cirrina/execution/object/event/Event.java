package at.ac.uibk.dps.cirrina.execution.object.event;

import static at.ac.uibk.dps.cirrina.utils.Uuid.insecureUuid;

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.utils.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Event, resembles an event as it is sent to state machine instances.
 */
public final class Event {

  /**
   * Event created time.
   */
  private final double createdTime;

  /**
   * Event ID, is unique.
   */
  private final String id;

  /**
   * Name of the event, is not unique.
   */
  private final String name;

  /**
   * Event channel.
   */
  private final EventChannel channel;

  /**
   * Event data.
   */
  private final List<ContextVariable> data;

  /**
   * Initializes this event. A random ID will be assigned to identify this event.
   *
   * @param name    Event name.
   * @param channel Event channel.
   * @param data    Event data.
   */
  public Event(String name, EventChannel channel, List<ContextVariable> data) {
    this.createdTime = Time.timeInMillisecondsSinceEpoch();
    this.id = insecureUuid().toString();
    this.name = name;
    this.channel = channel;
    this.data = data;
  }

  /**
   * Initializes this event.
   *
   * @param id      Event ID.
   * @param name    Event name.
   * @param channel Event channel.
   * @param data    Event data.
   */
  public Event(String id, String name, EventChannel channel, List<ContextVariable> data) {
    this.createdTime = Time.timeInMillisecondsSinceEpoch();
    this.id = id;
    this.name = name;
    this.channel = channel;
    this.data = data;
  }

  /**
   * Initializes this event.
   *
   * @param createdTime Event created time.
   * @param id          Event ID.
   * @param name        Event name.
   * @param channel     Event channel.
   * @param data        Event data.
   */
  public Event(
    double createdTime,
    String id,
    String name,
    EventChannel channel,
    List<ContextVariable> data
  ) {
    this.createdTime = createdTime;
    this.id = id;
    this.name = name;
    this.channel = channel;
    this.data = data;
  }

  /**
   * Ensure that an event only has evaluated event data.
   *
   * @param event  Event.
   * @param extent Extent.
   * @return Event with evaluated event data.
   * @throws UnsupportedOperationException If a event data variable could not be evaluated.
   */
  public static Event ensureHasEvaluatedData(Event event, Extent extent)
    throws UnsupportedOperationException {
    var data = new ArrayList<ContextVariable>();

    for (var variable : event.getData()) {
      try {
        data.add(variable.evaluate(extent));
      } catch (UnsupportedOperationException e) {
        throw new UnsupportedOperationException(
          "The event data variable '%s' could not be evaluated".formatted(variable.name()),
          e
        );
      }
    }

    return new Event(event.getName(), event.getChannel(), data);
  }

  public Event withData(List<ContextVariable> data) {
    return new Event(name, channel, data);
  }

  /**
   * Returns a string representation.
   *
   * @return String representation.
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * Returns a hash code representation.
   *
   * @return Hash code representation.
   */
  @Override
  public int hashCode() {
    return Objects.hash(id, name, channel, data);
  }

  /**
   * Returns the created time.
   *
   * @return Created time.
   */
  public double getCreatedTime() {
    return createdTime;
  }

  /**
   * Returns the ID.
   *
   * @return ID.
   */
  public String getId() {
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
   * Returns the channel.
   *
   * @return Channel.
   */
  public EventChannel getChannel() {
    return channel;
  }

  /**
   * Returns the data.
   *
   * @return Data.
   */
  public List<ContextVariable> getData() {
    return data;
  }
}
