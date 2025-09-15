package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.NatsContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.NatsEventHandler
import at.ac.uibk.dps.cirrina.execution.service.OptimalServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext

/** Cirrina entry class. */
class Cirrina {

  companion object {
    // Logger for Cirrina.
    val logger = LogManager.getLogger()

    init {
      // Static initialization cannot occur more than once
      setupLogging()
      setupnewHealthService()
    }

    // Set up the logger.
    private fun setupLogging() {
      val loggerContext = LogManager.getContext(false) as LoggerContext

      val loggerConfig = loggerContext.configuration.getLoggerConfig(logger.name)
      loggerConfig.level = Level.INFO

      loggerContext.updateLoggers()
    }

    // Create the health service.
    private fun setupnewHealthService(): HealthService {
      return try {
        HealthService(EnvironmentVariables.healthPort.get())
      } catch (e: RuntimeException) {
        throw RuntimeException("Failed to start the health service: $e", e)
      }
    }
  }

  /** Run Cirrina as configured. */
  fun run() {
    try {
      newEventHandler().use { eventHandler ->
        eventHandler.subscribe(NatsEventHandler.GLOBAL_SOURCE, "*")
        eventHandler.subscribe(NatsEventHandler.PERIPHERAL_SOURCE, "*")

        newPersistentContext().use { persistentContext ->
          val openTelemetry = getOpenTelemetry()

          val services = ServiceImplementationBuilder.from(listOf()).build()
          val serviceImplementationSelector = OptimalServiceImplementationSelector(services)

          val runtime =
            Runtime(openTelemetry, serviceImplementationSelector, eventHandler, persistentContext)

          runtime.run(
            EnvironmentVariables.applicationPath.get(),
            EnvironmentVariables.instantiate.get(),
          )

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
    when (EnvironmentVariables.persistentContextProvider.get()) {
      PersistentContextProvider.NATS -> newNatsPersistentContext()
      else ->
        throw ConfigurationError.Unknown(
          "persistent context",
          EnvironmentVariables.persistentContextProvider.get(),
        )
    }

  // Construct a new NATS persistent context as configured.
  private fun newNatsPersistentContext(): NatsContext =
    NatsContext(
      false,
      EnvironmentVariables.natsPersistentContextUrl.get(),
      EnvironmentVariables.natsPersistentContextBucket.get(),
    )

  // Construct a new OpenTelemetry instance as configured.
  private fun getOpenTelemetry(): OpenTelemetry =
    AutoConfiguredOpenTelemetrySdk.initialize().openTelemetrySdk
}

fun main() {
  Cirrina().run()
}
