package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.di.DaggerCirrinaComponent
import at.ac.uibk.dps.cirrina.utils.getBuildVersion
import java.util.logging.LogManager
import mu.KotlinLogging
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE

private val logger = KotlinLogging.logger {}

class Cirrina {

  fun run() {

    val component = DaggerCirrinaComponent.create()

    logger.info { "cirrina version ${getBuildVersion()}" }
    logger.info { component.identifier() }

    runCatching {
        component.eventHandler().use { _ ->
          component.persistentContext().use { _ -> component.runtime().run() }
        }

        // Flush the metrics
        component.meterRegistry().close()
      }
      .onFailure { ex -> logger.error(ex) { "a fatal error occurred during runtime execution" } }
  }

  companion object {
    const val ETCD_CONNECTION_TIMEOUT = 1000L

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
