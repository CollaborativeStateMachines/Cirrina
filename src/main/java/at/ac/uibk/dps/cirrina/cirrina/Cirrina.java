package at.ac.uibk.dps.cirrina.cirrina;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public abstract class Cirrina {

  /**
   * CirrinaRuntime logger.
   */
  static final Logger logger = LogManager.getLogger();

  public static void main(String... argv) {
    // Set up logging
    setupLogging();

    try (final var healthService = newHealthService()) {
      new CirrinaRuntime().run();
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
  private static HealthService newHealthService() {
    try {
      return new HealthService(EnvironmentVariables.INSTANCE.getHealthPort().get());
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
