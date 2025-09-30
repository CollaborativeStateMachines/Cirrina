package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.NatsContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.NatsEventHandler
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.io.parsing.CsmParser
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext

/** Cirrina entry class. */
class Cirrina {
  companion object {
    const val NATS_CONNECTION_TIMEOUT = 60000L

    val logger = LogManager.getLogger()

    init {
      setupLogging()
      setupnewHealthService()
    }

    // Set up the logger.
    private fun setupLogging() {
      (LogManager.getContext(false) as LoggerContext).apply {
        configuration.getLoggerConfig(logger.name).level = Level.INFO
        updateLoggers()
      }
    }

    // Create the health service.
    private fun setupnewHealthService(): HealthService =
      runCatching { HealthService(EnvironmentVariables.healthPort.get()) }
        .getOrElse { e -> throw RuntimeException("Failed to start the health service: $e", e) }
  }

  /** Run Cirrina as configured. */
  fun run() {
    try {
      newEventHandler()
        .apply {
          if (this is NatsEventHandler) {
            awaitInitialConnection(NATS_CONNECTION_TIMEOUT)
          }
        }
        .use { eventHandler ->
          eventHandler.subscribe(NatsEventHandler.GLOBAL_SOURCE, "*")
          eventHandler.subscribe(NatsEventHandler.PERIPHERAL_SOURCE, "*")

          newPersistentContext()
            .apply {
              if (this is NatsContext) {
                awaitInitialConnection(NATS_CONNECTION_TIMEOUT)
              }
            }
            .use { persistentContext ->
              val openTelemetry = getOpenTelemetry()
              val serviceImplementationSelector =
                RandomServiceImplementationSelector(
                  ServiceImplementationBuilder.from(
                      CsmParser.parseServiceImplementationBindings(
                          EnvironmentVariables.serviceBindingsPath.get()
                        )
                        .bindings
                    )
                    .build()
                )

              Runtime(
                  EnvironmentVariables.appPath.get(),
                  EnvironmentVariables.instantiate.get(),
                  openTelemetry,
                  serviceImplementationSelector,
                  eventHandler,
                  persistentContext,
                )
                .run()

              logger.info("Done running")
            }
        }
    } catch (e: EnvironmentVariableError) {
      logger.error("There is an error in the current configuration, because: '${e.message}'", e)
    } catch (e: Exception) {
      logger.error("There was an unknown in the runtime execution, because: '${e.message}'", e)
    }
  }

  // Construct a new event handler as configured.
  private fun newEventHandler(): EventHandler =
    when (EnvironmentVariables.eventProvider.get()) {
      EventProvider.NATS -> newNatsEventHandler()
      else ->
        throw ConfigurationError.Unknown(
          "Unknown event handler",
          EnvironmentVariables.eventProvider.get(),
        )
    }

  // Construct a new NATS event handler as configured.
  private fun newNatsEventHandler(): NatsEventHandler =
    NatsEventHandler(EnvironmentVariables.natsEventUrl.get())

  // Construct a new persistent context based as configured.
  private fun newPersistentContext(): Context =
    when (EnvironmentVariables.contextProvider.get()) {
      PersistentContextProvider.NATS -> newNatsPersistentContext()
      else ->
        throw ConfigurationError.Unknown(
          "persistent context",
          EnvironmentVariables.contextProvider.get(),
        )
    }

  // Construct a new NATS persistent context as configured.
  private fun newNatsPersistentContext(): NatsContext =
    NatsContext(
      false,
      EnvironmentVariables.natsContextUrl.get(),
      EnvironmentVariables.natsContextBucket.get(),
    )

  // Construct a new OpenTelemetry instance as configured.
  private fun getOpenTelemetry(): OpenTelemetry =
    AutoConfiguredOpenTelemetrySdk.initialize().openTelemetrySdk
}

fun main() {
  Cirrina().run()
}
