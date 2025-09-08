package at.ac.uibk.dps.cirrina.runtime.online;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass;
import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.csml.description.Csml.CollaborativeStateMachineDescription;
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions;
import at.ac.uibk.dps.cirrina.execution.object.context.NatsContext;
import at.ac.uibk.dps.cirrina.execution.object.event.NatsEventHandler;
import at.ac.uibk.dps.cirrina.io.description.DescriptionParser;
import at.ac.uibk.dps.cirrina.runtime.OnlineRuntime;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class PingPongTest {

  private static CollaborativeStateMachineClass collaborativeStateMachineClass;

  private static CuratorFramework getCuratorFramework() {
    final var zooKeeperConnectString = System.getenv("ZOOKEEPER_CONNECT_STRING");

    return CuratorFrameworkFactory.builder()
      .connectString(zooKeeperConnectString)
      .retryPolicy(new ExponentialBackoffRetry(1000, 3))
      .connectionTimeoutMs(3000)
      .sessionTimeoutMs(3000)
      .build();
  }

  @BeforeAll
  public static void setUp() {
    var json = DefaultDescriptions.pingPong;

    var parser = new DescriptionParser<>(CollaborativeStateMachineDescription.class);
    Assertions.assertDoesNotThrow(() -> {
      collaborativeStateMachineClass = CollaborativeStateMachineClassBuilder.from(
        parser.parse(json)
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
          final var curatorFramework = getCuratorFramework();
          curatorFramework.start();

          final var runtime = new OnlineRuntime(
            "runtime",
            eventHandler,
            persistentContext,
            openTelemetry,
            curatorFramework,
            false
          );
          //runtime.run();

          curatorFramework.close();
        }
      }
    });
  }
}
