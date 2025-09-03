package at.ac.uibk.dps.cirrina.cirrina;

import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.context.NatsContext;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.execution.object.event.NatsEventHandler;
import at.ac.uibk.dps.cirrina.runtime.OnlineRuntime;
import at.ac.uibk.dps.cirrina.utils.Id;
import info.schnatterer.mobynamesgenerator.MobyNamesGenerator;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.io.IOException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * CirrinaRuntime, is the entry-point to the runtime system.
 */
public class CirrinaRuntime extends Cirrina {

  /**
   * Runtime ID.
   */
  private final Id runtimeId = new Id();

  /**
   * Run the runtime.
   */
  @Override
  public void run() {
    // Connect to event system
    try (final var eventHandler = newEventHandler()) {
      eventHandler.subscribe(NatsEventHandler.GLOBAL_SOURCE, "*");
      eventHandler.subscribe(NatsEventHandler.PERIPHERAL_SOURCE, "*");

      // Connect to persistent context system and ZooKeeper
      try (
        final var persistentContext = newPersistentContext();
        final var curatorFramework = newCuratorFramework()
      ) {
        // Connect to ZooKeeper
        curatorFramework.start();

        // Acquire OpenTelemetry instance
        final var openTelemetry = getOpenTelemetry();

        // Generate a runtime name
        final var name = MobyNamesGenerator.getRandomName();

        // Create the shared runtime
        final var runtime = new OnlineRuntime(
          name,
          eventHandler,
          persistentContext,
          openTelemetry,
          curatorFramework,
          EnvironmentVariables.INSTANCE.getDeleteJob().get()
        );

        logger.info("Starting runtime: {}", name);

        // Run, will return when finished
        runtime.run();

        logger.info("Done running");
      }
    } catch (InterruptedException e) {
      logger.info("Interrupted.");

      Thread.currentThread().interrupt();
    } catch (Exception e) {
      logger.error("Could not initialize the shared runtime", e);
    }
  }

  /**
   * Constructs a new event handler according to the provided arguments.
   *
   * @return Event handler.
   * @throws IOException              If the event handler could not be constructed.
   * @throws IllegalArgumentException If the event handler provided is not known.
   */
  protected EventHandler newEventHandler() throws IOException, IllegalArgumentException {
    switch (EnvironmentVariables.INSTANCE.getEventProvider().get()) {
      case NATS -> {
        return newNatsEventHandler();
      }
    }

    throw new IllegalArgumentException(
      "Unknown event handler '%s'".formatted(EnvironmentVariables.INSTANCE.getEventProvider().get())
    );
  }

  /**
   * Constructs a new NATS event handler according to the provided arguments.
   *
   * @return Event handler.
   * @throws IOException If the event handler could not be constructed.
   */
  private NatsEventHandler newNatsEventHandler() throws IOException {
    return new NatsEventHandler(EnvironmentVariables.INSTANCE.getNatsEventUrl().get());
  }

  /**
   * Constructs a new persistent context according to the provided arguments.
   *
   * @return Persistent context.
   * @throws IOException              If the event handler could not be constructed.
   * @throws IllegalArgumentException If the persistent context provided is not known.
   */
  protected Context newPersistentContext() throws IOException, IllegalArgumentException {
    switch (EnvironmentVariables.INSTANCE.getPersistentContextProvider().get()) {
      case NATS -> {
        return newNatsPersistentContext();
      }
    }

    throw new IllegalArgumentException(
      "Unknown persistent context '%s'".formatted(
        EnvironmentVariables.INSTANCE.getPersistentContextProvider().get()
      )
    );
  }

  /**
   * Constructs a new NATS persistent context according to the provided arguments.
   *
   * @return Persistent context.
   * @throws IOException If the persistent context could not be constructed.
   */
  private NatsContext newNatsPersistentContext() throws IOException {
    return new NatsContext(
      false,
      EnvironmentVariables.INSTANCE.getNatsPersistentContextUrl().get(),
      EnvironmentVariables.INSTANCE.getNatsPersistentContextBucket().get()
    );
  }

  /**
   * Constructs a new Curator framework according to the provided arguments.
   *
   * @return Curator framework.
   */
  public CuratorFramework newCuratorFramework() {
    return CuratorFrameworkFactory.builder()
      .connectString(EnvironmentVariables.INSTANCE.getZookeeperUrl().get())
      .retryPolicy(new ExponentialBackoffRetry(1000, 3))
      .connectionTimeoutMs(EnvironmentVariables.INSTANCE.getZookeeperTimeout().get())
      .sessionTimeoutMs(EnvironmentVariables.INSTANCE.getZookeeperSessionTimeout().get())
      .build();
  }

  /**
   * Returns the OpenTelemetry SDK instance.
   *
   * @return OpenTelemetry SDK.
   */
  protected OpenTelemetry getOpenTelemetry() {
    return AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
  }
}
