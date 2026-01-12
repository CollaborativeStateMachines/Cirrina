package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.EtcdContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.NatsEventHandler
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.io.parsing.CsmParser
import com.google.common.flogger.FluentLogger
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import java.net.URI
import java.util.logging.LogManager
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

/** Cirrina entry class. */
class Cirrina {
  companion object {
    const val NATS_CONNECTION_TIMEOUT = 60000L
    const val ETCD_CONNECTION_TIMEOUT = 60000L

    init {
      ToStringBuilder.setDefaultStyle(SIMPLE_STYLE)

      runCatching {
          Cirrina::class.java.getResourceAsStream("/logging.properties")?.use { inputStream ->
            LogManager.getLogManager().readConfiguration(inputStream)
          } ?: logger.atWarning().log("Logging properties file not found")
        }
        .onFailure { ex ->
          logger.atSevere().withCause(ex).log("Could not load logging properties")
        }

      logger.atFine().log("Starting health service")
      runCatching { HealthService(EnvironmentVariables.healthPort.get()) }
        .getOrElse { e -> logger.atSevere().withCause(e).log("Could not start the health service") }
    }
  }

  /** Run Cirrina as configured. */
  fun run() {
    try {
      logger.atFine().log("Creating the event handler")
      newEventHandler()
        .apply {
          if (this is NatsEventHandler) {
            logger.atFine().log("Awaiting connection to NATS as the event handler")
            awaitInitialConnection(NATS_CONNECTION_TIMEOUT)
          }
        }
        .use { eventHandler ->
          logger.atFiner().log("Subscribing to event sources")
          eventHandler.subscribe(NatsEventHandler.GLOBAL_SOURCE, "*")
          eventHandler.subscribe(NatsEventHandler.PERIPHERAL_SOURCE, "*")

          logger.atFine().log("Creating the persistent context")
          newPersistentContext()
            .apply {
              if (this is EtcdContext) {
                logger.atFine().log("Awaiting connection to Etcd as the persistent context")
                awaitInitialConnection(ETCD_CONNECTION_TIMEOUT)
              }
            }
            .use { persistentContext ->
              val openTelemetry = getOpenTelemetry()

              logger.atFine().log("Loading service implementation bindings")
              var serviceImplementationBindings =
                CsmParser.parseServiceImplementationBindings(
                    URI(EnvironmentVariables.serviceBindingsPath.get())
                  )
                  .bindings

              logger.atFine().log("Creating the runtime")
              val runtime =
                Runtime(
                  URI(EnvironmentVariables.appPath.get()),
                  EnvironmentVariables.instantiate.get(),
                  openTelemetry,
                  RandomServiceImplementationSelector(
                    ServiceImplementationBuilder.from(serviceImplementationBindings).build()
                  ),
                  eventHandler,
                  persistentContext,
                )

              logger.atFine().log("Running the runtime")
              runtime.run()
            }
        }
    } catch (e: ConfigurationError) {
      logger.atSevere().withCause(e).log("There is an error in the current configuration")
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("There was an unknown in the runtime execution")
    }
  }

  // Construct a new event handler as configured.
  private fun newEventHandler(): EventHandler =
    when (EnvironmentVariables.eventProvider.get()) {
      EventProvider.NATS -> newNatsEventHandler()
    }

  // Construct a new NATS event handler as configured.
  private fun newNatsEventHandler(): NatsEventHandler =
    NatsEventHandler(EnvironmentVariables.natsEventUrl.get())

  // Construct a new persistent context based as configured.
  private fun newPersistentContext(): Context =
    when (EnvironmentVariables.contextProvider.get()) {
      PersistentContextProvider.ETCD -> newEtcdPersistentContext()
    }

  // Construct a new Etcd persistent context as configured.
  private fun newEtcdPersistentContext(): EtcdContext =
    EtcdContext(false, listOf(EnvironmentVariables.etcdContextUrl.get()))

  // Construct a new OpenTelemetry instance as configured.
  private fun getOpenTelemetry(): OpenTelemetry =
    AutoConfiguredOpenTelemetrySdk.initialize().openTelemetrySdk
}

fun main() {
  Cirrina().run()
}
