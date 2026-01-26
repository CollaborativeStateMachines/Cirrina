package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.di.DaggerCirrinaComponent
import java.util.logging.LogManager
import mu.KotlinLogging
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE

private val logger = KotlinLogging.logger {}

class Cirrina {

  fun run() {
    val component = DaggerCirrinaComponent.create()

    runCatching {
        component.eventHandler().use { _ ->
          component.persistentContext().use { _ -> component.runtime().run() }
        }
      }
      .onFailure { ex -> logger.error(ex) { "a fatal error occurred during runtime execution" } }
  }

  companion object {
    /** Timeout for the NATS connection. */
    const val NATS_CONNECTION_TIMEOUT = 60000L

    /** Timeout for the ETCD connection. */
    const val ETCD_CONNECTION_TIMEOUT = 60000L

    init {
      configureStringBuilder()
      configureLogging()
    }

    private fun configureStringBuilder() = ToStringBuilder.setDefaultStyle(SHORT_PREFIX_STYLE)

    private fun configureLogging() =
      runCatching {
          Cirrina::class.java.getResourceAsStream("/logging.properties")?.use {
            LogManager.getLogManager().readConfiguration(it)
          } ?: logger.warn { "logging properties file not found" }
        }
        .onFailure { logger.error(it) { "could not load logging properties" } }
  }
}

fun main() = Cirrina().run()
