package at.ac.uibk.dps.cirrina.execution.object.exchange;

import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.EventChannel;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Event exchange, responsible for converting an event object to a consistent exchange format, using Protocol Buffers.
 * <p>
 * See the exchange protos for a protocol description.
 */
public class EventExchange {

  /**
   * The event object.
   */
  private final Event event;

  /**
   * Initializes this event exchange instance.
   *
   * @param event Event object.
   */
  public EventExchange(Event event) {
    this.event = event;
  }

  /**
   * Construct an event exchange from byte data.
   *
   * @param data Byte data.
   * @return Event exchange.
   * @throws UnsupportedOperationException If the event could not be read.
   * @throws UnsupportedOperationException If the event has an unrecognized channel.
   */
  public static EventExchange fromBytes(byte[] data) throws UnsupportedOperationException {
    try {
      final var eventProto = EventProtos.Event.parseFrom(data);

      return new EventExchange(fromProto(eventProto));
    } catch (InvalidProtocolBufferException e) {
      throw new UnsupportedOperationException("Received an event with an unsupported payload", e);
    }
  }

  /**
   * Construct an event object from a proto.
   *
   * @param proto Event proto.
   * @return Event object.
   * @throws UnsupportedOperationException If the event has an unrecognized channel.
   */
  public static Event fromProto(EventProtos.Event proto) {
    try {
      final var createdTime = proto.getCreatedTime();
      final var id = proto.getId();
      final var name = proto.getName();
      final var channel = EventChannel.valueOf(proto.getChannel().name());
      final var data = proto
        .getDataList()
        .stream()
        .map(ContextVariableExchange::fromProto)
        .toList();

      return new Event(createdTime, id, name, channel, data);
    } catch (IllegalArgumentException e) {
      throw new UnsupportedOperationException("Event has an unrecognized channel", e);
    }
  }

  /**
   * Converts this exchange instance to bytes.
   * <p>
   * Event data must be evaluated before conversion to bytes can succeed.
   *
   * @return Bytes.
   * @throws IllegalStateException If the event has unevaluated data.
   */
  public byte[] toBytes() throws IllegalStateException {
    if (event.getData().stream().anyMatch(ContextVariable::isLazy)) {
      throw new IllegalStateException(
        "Event '%s' has unevaluated event data".formatted(event.getName())
      );
    }

    return toProto().toByteArray();
  }

  /**
   * Returns a proto from this exchange.
   *
   * @return Proto.
   * @throws UnsupportedOperationException If the event has an unrecognized channel.
   */
  public EventProtos.Event toProto() throws UnsupportedOperationException {
    EventProtos.Event.Channel channel;

    try {
      channel = EventProtos.Event.Channel.valueOf(event.getChannel().name());
    } catch (IllegalArgumentException e) {
      throw new UnsupportedOperationException(
        "Event '%s' has an unrecognized channel".formatted(event.getName()),
        e
      );
    }

    final var dataProtos = event
      .getData()
      .stream()
      .map(event -> new ContextVariableExchange(event).toProto())
      .toList();

    return EventProtos.Event.newBuilder()
      .setCreatedTime(event.getCreatedTime())
      .setId(event.getId())
      .setName(event.getName())
      .setChannel(channel)
      .addAllData(dataProtos)
      .build();
  }

  /**
   * Returns the event object.
   *
   * @return Event object.
   */
  public Event getEvent() {
    return event;
  }
}
