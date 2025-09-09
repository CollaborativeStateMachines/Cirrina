package at.ac.uibk.dps.cirrina.runtime.online;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass;
import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions;
import at.ac.uibk.dps.cirrina.execution.object.context.NatsContext;
import at.ac.uibk.dps.cirrina.execution.object.event.NatsEventHandler;
import at.ac.uibk.dps.cirrina.io.description.CsmlParser;
import at.ac.uibk.dps.cirrina.runtime.OnlineRuntime;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class PingPongTest {

  private static CollaborativeStateMachineClass collaborativeStateMachineClass;

  @BeforeAll
  public static void setUp() {
    var json = DefaultDescriptions.pingPong;

    Assertions.assertDoesNotThrow(() -> {
      collaborativeStateMachineClass = CollaborativeStateMachineClassBuilder.from(
        CsmlParser.parse(json)
      ).build();
    });
  }

  @Disabled
  @Test
  void testPingPongExecute() {
    final var natsServerURL = System.getenv("NATS_SERVER_URL");
    final var natsBucketName = System.getenv("NATS_BUCKET_NAME");
    final var zooKeeperConnectString = System.getenv("ZOOKEEPER_CONNECT_STRING");

    Assumptions.assumeFalse(natsServerURL == null, "Skipping online runtime test");
    Assumptions.assumeFalse(natsBucketName == null, "Skipping online runtime test");
    Assumptions.assumeFalse(zooKeeperConnectString == null, "Skipping online runtime test");

    Assertions.assertDoesNotThrow(() -> {
      final var openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();

      try (final var eventHandler = new NatsEventHandler(natsServerURL)) {
        try (final var persistentContext = new NatsContext(true, natsServerURL, natsBucketName)) {
          final var runtime = new OnlineRuntime(
            "runtime",
            eventHandler,
            persistentContext,
            openTelemetry
          );
          //runtime.run();
        }
      }
    });
  }
}
