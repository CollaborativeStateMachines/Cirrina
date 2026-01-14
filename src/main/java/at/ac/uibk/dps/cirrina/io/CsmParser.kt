package at.ac.uibk.dps.cirrina.io

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import java.net.URI
import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.SecurityManager
import org.pkl.core.SecurityManagers

/**
 * Security manager for CSM. Wraps the standard security manager which is private in the Pkl
 * library.
 */
class CsmSecurityManager : SecurityManager {
  val standard = SecurityManagers.defaultManager

  /** @see SecurityManager#checkResolveModule(URI) */
  override fun checkResolveModule(uri: URI) {
    standard.checkResolveModule(uri)
  }

  /** @see SecurityManager#checkImportModule(URI) */
  override fun checkImportModule(importingModule: URI, importedModule: URI) {}

  /** @see SecurityManager#checkReadResource(URI) */
  override fun checkReadResource(resource: URI) {
    standard.checkReadResource(resource)
  }

  /** @see SecurityManager#checkResolveResource(URI) */
  override fun checkResolveResource(resource: URI) {
    standard.checkResolveResource(resource)
  }
}

/** CSM parser, used to parse CSM Pkl files. */
object CsmParser {
  // Build an evaluator that works with CSM modules.
  private fun evaluator(): ConfigEvaluator {
    // Create a preconfigured ConfigEvaluatorBuilder
    val builder = ConfigEvaluatorBuilder.preconfigured()

    // Provide our custom security manager
    builder.securityManager = CsmSecurityManager()

    // Build and load the main module
    return builder.build()
  }

  /**
   * Parse a CSML Pkl module at a URI. Returns an instance of Csml upon success. Any errors will
   * result in an IllegalArgumentException being thrown.
   *
   * @param mainModuleUri Main Pkl module URI.
   * @return Csml model.
   * @throws IllegalArgumentException If an error occurs.
   */
  fun parseCsml(mainModuleUri: URI): Csml {
    try {
      evaluator().use { evaluator ->
        return evaluator.evaluate(ModuleSource.uri(mainModuleUri)).`as`(Csml::class.java)
      }
    } catch (e: Exception) {
      throw IllegalArgumentException("Parsing error", e)
    }
  }

  /**
   * Parse a service implementation bindings Pkl module at a URI. Returns an instance of
   * ServiceImplementationBindings upon success. Any errors will result in an
   * IllegalArgumentException being thrown.
   *
   * @param serviceImplementationBindingsModuleUri Main Pkl module URI.
   * @return Csml model.
   * @throws IllegalArgumentException If an error occurs.
   */
  fun parseServiceImplementationBindings(
    serviceImplementationBindingsModuleUri: URI
  ): ServiceImplementationBindings {
    try {
      evaluator().use { evaluator ->
        return evaluator
          .evaluate(ModuleSource.uri(serviceImplementationBindingsModuleUri))
          .`as`(ServiceImplementationBindings::class.java)
      }
    } catch (e: Exception) {
      throw IllegalArgumentException("Parsing error", e)
    }
  }
}
