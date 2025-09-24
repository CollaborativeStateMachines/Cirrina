package at.ac.uibk.dps.cirrina.io.parsing

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import java.net.URI
import java.util.*
import java.util.regex.Pattern
import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.SecurityManager
import org.pkl.core.module.ModuleKey
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.module.ResolvedModuleKey
import org.pkl.core.util.IoUtils

/** CsmModuleKey, used to load CSM modules from the classpath. */
class CsmModuleKey(private val uri: URI) : ModuleKey, ResolvedModuleKey {

  /** Get the original module key. */
  override fun getOriginal(): ModuleKey = this

  /** Get the URI of the module. */
  override fun getUri(): URI = uri

  /** Load the source code of the module. */
  override fun loadSource(): String =
    IoUtils.readClassPathResourceAsString(javaClass, "/pkl/csm/${uri.schemeSpecificPart}.pkl")

  /** Resolve the module. */
  override fun resolve(securityManager: SecurityManager): ResolvedModuleKey {
    securityManager.checkResolveModule(uri)
    return this
  }

  /** Csm modules are always hierarchical. */
  override fun hasHierarchicalUris(): Boolean = true

  /** Csm modules cannot be globbed. */
  override fun isGlobbable(): Boolean = false
}

/** Factory for CsmModuleKey, converts csm: modules to CsmModuleKey. */
class CsmModuleKeyFactory : ModuleKeyFactory {
  /** Converts a URI into a CsmModuleKey if the URI scheme is csm. */
  override fun create(uri: URI): Optional<ModuleKey> =
    Optional.ofNullable(
      uri.takeIf { it.scheme.equals("csm", ignoreCase = true) }?.let { CsmModuleKey(it) }
    )
}

/** CSM parser, used to parse CSM Pkl files. */
object CsmParser {
  // Build an evaluator that works with CSM modules.
  private fun evaluator(): ConfigEvaluator {
    // Create a preconfigured ConfigEvaluatorBuilder
    val builder = ConfigEvaluatorBuilder.preconfigured()

    // Mark the CSM module as an allowed module
    builder.allowedModules.add(Pattern.compile("csm:.*"))

    // Add the CsmModuleKeyFactory to the builder
    builder.evaluatorBuilder.addModuleKeyFactory(CsmModuleKeyFactory())

    // Build and load the main module
    return builder.build()
  }

  fun parseCsml(mainModulePath: URI): Csml = parseCsml(mainModulePath.toString())

  /**
   * Parse a CSML Pkl module at a path. Returns an instance of Csml upon success. Any errors will
   * result in an IllegalArgumentException being thrown.
   *
   * @param mainModulePath Main Pkl module path.
   * @return Csml model.
   * @throws IllegalArgumentException If an error occurs.
   */
  fun parseCsml(mainModulePath: String): Csml {
    try {
      evaluator().use { evaluator ->
        return evaluator.evaluate(ModuleSource.file(mainModulePath)).`as`(Csml::class.java)
      }
    } catch (e: Exception) {
      throw IllegalArgumentException("Parsing error", e)
    }
  }

  /**
   * Parse a service implementation bindings Pkl module at a path. Returns an instance of
   * ServiceImplementationBindings upon success. Any errors will result in an
   * IllegalArgumentException being thrown.
   *
   * @param mainModulePath Main Pkl module path.
   * @return Csml model.
   * @throws IllegalArgumentException If an error occurs.
   */
  fun parseServiceImplementationBindings(
    serviceImplementationBindingsModulePath: String
  ): ServiceImplementationBindings {
    try {
      evaluator().use { evaluator ->
        return evaluator
          .evaluate(ModuleSource.file(serviceImplementationBindingsModulePath))
          .`as`(ServiceImplementationBindings::class.java)
      }
    } catch (e: Exception) {
      throw IllegalArgumentException("Parsing error", e)
    }
  }
}
