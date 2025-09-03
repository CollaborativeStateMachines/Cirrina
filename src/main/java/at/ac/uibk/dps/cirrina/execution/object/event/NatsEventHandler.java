package at.ac.uibk.dps.cirrina.execution.object.event;

import at.ac.uibk.dps.cirrina.execution.object.exchange.EventExchange;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import java.io.IOException;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NatsEventHandler extends EventHandler {

  public static final String GLOBAL_SOURCE = "global";
  public static final String PERIPHERAL_SOURCE = "peripheral";

  private static final Logger logger = LogManager.getLogger();

  private final Connection connection;

  private final Dispatcher dispatcher;

  public NatsEventHandler(String natsUrl) throws IOException {
    // Attempt to connect to the NATS server
    try {
      connection = Nats.connect(natsUrl);
    } catch (InterruptedException | IOException e) {
      Thread.currentThread().interrupt();

      throw new IOException("Could not connect to the NATS server", e);
    }

    // Create a message dispatcher (asynchronous)
    dispatcher = connection.createDispatcher(this::handle);
  }

  private void handle(Message message) {
    // Reconstruct the event from the message data, if possible
    try {
      var event = EventExchange.fromBytes(message.getData()).getEvent();

      propagateEvent(event);
    } catch (UnsupportedOperationException e) {
      logger.debug("A message could not be read as an event: {}", e.getMessage());
    }
  }

  @Override
  public void sendEvent(Event event, String source) throws IOException {
    try {
      var data = new EventExchange(event).toBytes();

      // * is used as a wildcard, for more information, refer to the NATS documentation:
      // https://docs.nats.io/using-nats/developer/receiving/wildcards
      var subject = Stream.of(event.getChannel())
        .map(channel ->
          switch (channel) {
            case EXTERNAL -> String.format("%s.%s", source, event.getName());
            case GLOBAL -> String.format("%s.%s", GLOBAL_SOURCE, event.getName());
            default -> throw new IllegalArgumentException(
              String.format("Unsupported channel '%s'", channel)
            );
          }
        )
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No channel specified for the event"));

      connection.publish(subject, data);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new IOException("Could not send event through NATS", e);
    }
  }

  @Override
  public void subscribe(String eventName) {
    dispatcher.subscribe(String.format("*.%s", eventName));
  }

  @Override
  public void unsubscribe(String eventName) {
    dispatcher.unsubscribe(String.format("*.%s", eventName));
  }

  @Override
  public void subscribe(String source, String eventName) {
    dispatcher.subscribe(String.format("%s.%s", source, eventName));
  }

  @Override
  public void unsubscribe(String source, String eventName) {
    dispatcher.unsubscribe(String.format("%s.%s", source, eventName));
  }

  @Override
  public void close() throws IOException {
    try {
      connection.closeDispatcher(dispatcher);

      connection.close();
    } catch (InterruptedException e) {
      throw new IOException("Failed to close NATS persistent context", e);
    }
  }
}
