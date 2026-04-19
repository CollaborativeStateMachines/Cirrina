package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.di.DaggerCirrinaComponent
import at.ac.uibk.dps.cirrina.util.getBuildVersion
import java.util.logging.LogManager
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class Cirrina {
  fun run() {
    val component = DaggerCirrinaComponent.create()

    logger.info { "cirrina version ${getBuildVersion()}" }
    logger.info { component.identifier() }

    runCatching { component.persistentContext().use { _ -> component.runtime().run() } }
      .onFailure { ex -> logger.error(ex) { "a fatal error occurred during runtime execution" } }
  }

  companion object {
    init {
      configureLogging()
    }

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
