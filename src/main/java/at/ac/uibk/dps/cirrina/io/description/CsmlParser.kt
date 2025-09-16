package at.ac.uibk.dps.cirrina.io.description

import at.ac.uibk.dps.cirrina.csml.description.Csml
import java.net.URI
import java.util.*
import java.util.regex.Pattern
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

/** Csml parser, used to parse a Pkl file into a Csml object. */
object CsmlParser {
  /**
   * Parse a Pkl module at a path. Returns an instance of Csml upon success. Any errors will result
   * in an IllegalArgumentException being thrown.
   *
   * @param mainModulePath Main Pkl module path.
   * @return Csml model.
   * @throws IllegalArgumentException If an error occurs.
   */
  fun parse(mainModulePath: String): Csml {
    try {
      // Create a preconfigured ConfigEvaluatorBuilder
      val builder = ConfigEvaluatorBuilder.preconfigured()

      // Mark the CSM module as an allowed module
      builder.allowedModules.add(Pattern.compile("csm:.*"))

      // Add the CsmModuleKeyFactory to the builder
      builder.evaluatorBuilder.addModuleKeyFactory(CsmModuleKeyFactory())

      // Build and load the main module
      builder.build().use { evaluator ->
        return evaluator.evaluate(ModuleSource.file(mainModulePath)).`as`(Csml::class.java)
      }
    } catch (e: Exception) {
      throw IllegalArgumentException("Parsing error", e)
    }
  }
}
