package at.ac.uibk.dps.cirrina.cirrina;

import at.ac.uibk.dps.cirrina.cirrina.Cirrina.Args.EventHandler;
import at.ac.uibk.dps.cirrina.cirrina.Cirrina.Args.PersistentContext;
import at.ac.uibk.dps.cirrina.observability.Tracing;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.beust.jcommander.UnixStyleUsageFormatter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public abstract class Cirrina {

  /**
   * NATS event handler-specific arguments.
   */
  final static class NatsEventHandlerArgs {

    @Parameter(names = {"--nats-event-url"}, description = "NATS server connection string for event handling")
    String natsUrl = "nats://localhost:4222/";
  }

  /**
   * NATS persistent context-specific arguments.
   */
  final static class NatsPersistentContextArgs {

    @Parameter(names = {"--nats-context-url"}, description = "NATS server connection string for managing persistent context")
    String natsUrl = "nats://localhost:4222/";

    @Parameter(names = {"--nats-bucket"}, description = "Bucket name used for storing the persistent context")
    String bucketName = "persistent";
  }

  /**
   * ZooKeeper-specific arguments.
   */
  final static class ZooKeeperArgs {

    @Parameter(names = "--zk-url", description = "ZooKeeper connection string")
    String connectString = "localhost:2181";

    @Parameter(names = "--zk-timeout", description = "Timeout for ZooKeeper connections, in milliseconds")
    int timeoutInMs = 3000;

    @Parameter(names = "--zk-session-timeout", description = "Session timeout for ZooKeeper, in milliseconds")
    int sessionTimeoutInMs = 3000;
  }

  /**
   * Runtime-specific arguments.
   */
  final static class RuntimeArgs {

    @ParametersDelegate
    final NatsEventHandlerArgs natsEventHandlerArgs = new NatsEventHandlerArgs();

    @ParametersDelegate
    final NatsPersistentContextArgs natsPersistentContextArgs = new NatsPersistentContextArgs();

    @ParametersDelegate
    final ZooKeeperArgs zooKeeperArgs = new ZooKeeperArgs();

    @Parameter(names = {"--event-handler", "-e"}, description = "Specifies the event handler type to use")
    EventHandler eventHandler = EventHandler.Nats;

    @Parameter(names = {"--persistent-context", "-p"}, description = "Specifies the persistent context type to use")
    PersistentContext persistentContext = PersistentContext.Nats;

    @Parameter(names = {"--delete-job", "-d"}, arity = 1, description = "Flag to delete the job after it is consumed")
    boolean deleteJob = true;
  }

  /**
   * Arguments.
   */
  final static class Args {

    @ParametersDelegate
    final RuntimeArgs runtimeArgs = new RuntimeArgs();

    @Parameter(names = {"--health-port", "-z"}, description = "Port number for the HTTP health check service")
    int healthPort = 0xCAFE;

    @Parameter(names = {"--manager", "-m"}, description = "Run the application in manager mode")
    boolean manager = false;

    @Parameter(names = {"--help", "-h"}, help = true, description = "Show this help message")
    private boolean help;

    enum Scheduler {
      RoundRobin
    }

    enum EventHandler {
      Nats
    }

    enum PersistentContext {
      Nats
    }
  }

  /**
   * CirrinaRuntime logger.
   */
  static final Logger logger = LogManager.getLogger();
  public static final Tracer tracer = GlobalOpenTelemetry.getTracer("Cirrina.tracing");
  public static final Tracing tracing = new Tracing();

  public static void main(String... argv) {
    // Set up logging
    setupLogging();

    // Construct shared arguments
    final var args = new Args();

    // Construct argument parser
    final var jc = JCommander.newBuilder()
        .addObject(args)
        .programName("cirrina")
        .columnSize(80)
        .build();

    jc.setUsageFormatter(new UnixStyleUsageFormatter(jc));

    try {
      // Parse arguments
      jc.parse(argv);

      if (args.help) {
        jc.usage();
      } else {
        try (final var healthService = newHealthService(args)) {
          // Instantiate the manager main
          if (args.manager) {
            new CirrinaManager(args).run();
          }
          // Instantiate the runtime main
          else {
            new CirrinaRuntime(args).run();
          }
        }
      }
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  /**
   * Constructs a health service.
   *
   * @return Health service.
   * @throws RuntimeException If the health service could not be started.
   */
  private static HealthService newHealthService(Args args) {
    try {
      return new HealthService(args.healthPort);
    } catch (RuntimeException e) {
      throw new RuntimeException("Failed to start the health service: " + e);
    }
  }

  /**
   * Set up the logger.
   */
  private static void setupLogging() {
    final var loggerContext = (LoggerContext) LogManager.getContext(false);
    final var loggerConfig = loggerContext.getConfiguration().getLoggerConfig(logger.getName());

    // Set log level
    loggerConfig.setLevel(Level.INFO);
    loggerContext.updateLoggers();
  }

  public abstract void run();
}
