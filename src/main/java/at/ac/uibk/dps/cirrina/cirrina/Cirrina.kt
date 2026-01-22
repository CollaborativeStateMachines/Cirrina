package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.EtcdContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.NatsEventHandler
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.io.CsmParser
import com.google.common.flogger.FluentLogger
import java.net.URI
import java.util.logging.LogManager
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

/** The primary orchestrator for the Cirrina runtime environment. */
class Cirrina {

  /** Bootstraps and executes the runtime. */
  fun run() {
    try {
      setupEventHandler().use { eventHandler ->
        setupPersistentContext().use { persistentContext ->
          buildRuntime(eventHandler, persistentContext).run()
        }
      }
    } catch (ex: Exception) {
      logger.atSevere().withCause(ex).log("a fatal error occurred during runtime execution")
    }
  }

  private fun setupEventHandler(): EventHandler =
    newEventHandler().also { handler ->
      if (handler is NatsEventHandler) {
        logger.atFine().log("awaiting NATS connection...")
        handler.awaitReady(NATS_CONNECTION_TIMEOUT)
        handler.subscribe(NatsEventHandler.GLOBAL_SOURCE, "*")
        handler.subscribe(NatsEventHandler.PERIPHERAL_SOURCE, "*")
      }
    }

  private fun setupPersistentContext(): Context =
    newPersistentContext().also { context ->
      if (context is EtcdContext) {
        logger.atFine().log("awaiting Etcd connection...")
        context.awaitReady(ETCD_CONNECTION_TIMEOUT)
      }
    }

  private fun buildRuntime(eventHandler: EventHandler, persistentContext: Context): Runtime =
    CsmParser.parseServiceImplementationBindings(
        URI(EnvironmentVariables.serviceBindingsPath.get())
      )
      .let { bindings ->
        Runtime(
          URI(EnvironmentVariables.appPath.get()),
          EnvironmentVariables.instantiate.get(),
          eventHandler,
          persistentContext,
          RandomServiceImplementationSelector(
            ServiceImplementationBuilder.from(bindings.bindings).build().getOrThrow()
          ),
        )
      }

  private fun newEventHandler(): EventHandler =
    when (EnvironmentVariables.eventProvider.get()) {
      EventProvider.NATS -> NatsEventHandler(EnvironmentVariables.natsEventUrl.get())
    }

  private fun newPersistentContext(): Context =
    when (EnvironmentVariables.contextProvider.get()) {
      PersistentContextProvider.ETCD ->
        EtcdContext(listOf(EnvironmentVariables.etcdContextUrl.get()))
    }

  companion object {
    const val NATS_CONNECTION_TIMEOUT = 60000L
    const val ETCD_CONNECTION_TIMEOUT = 60000L

    init {
      ToStringBuilder.setDefaultStyle(SIMPLE_STYLE)
      configureLogging()
      startHealthService()
    }

    private fun configureLogging() =
      runCatching {
          Cirrina::class.java.getResourceAsStream("/logging.properties")?.use {
            LogManager.getLogManager().readConfiguration(it)
          } ?: logger.atWarning().log("logging properties file not found")
        }
        .onFailure { logger.atSevere().withCause(it).log("could not load logging properties") }

    private fun startHealthService() =
      runCatching { HealthService(EnvironmentVariables.healthPort.get()) }
        .onFailure { logger.atSevere().withCause(it).log("could not start health service") }
  }
}

/** Global application entry point. */
fun main() = Cirrina().run()
