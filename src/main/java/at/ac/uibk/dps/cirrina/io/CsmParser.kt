package at.ac.uibk.dps.cirrina.io

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import java.net.URI
import org.pkl.config.java.ConfigEvaluator
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.SecurityManager
import org.pkl.core.SecurityManagers

class CsmSecurityManager : SecurityManager {
  val standard = SecurityManagers.defaultManager

  override fun checkResolveModule(uri: URI) {
    standard.checkResolveModule(uri)
  }

  override fun checkImportModule(importingModule: URI, importedModule: URI) {}

  override fun checkReadResource(resource: URI) {
    standard.checkReadResource(resource)
  }

  override fun checkResolveResource(resource: URI) {
    standard.checkResolveResource(resource)
  }
}

object CsmParser {
  private fun evaluator(): ConfigEvaluator {
    val builder = ConfigEvaluatorBuilder.preconfigured()

    builder.securityManager = CsmSecurityManager()

    return builder.build()
  }

  fun parseCsml(uri: URI): Csml {
    try {
      evaluator().use { evaluator ->
        return evaluator.evaluate(ModuleSource.uri(uri)).`as`(Csml::class.java)
      }
    } catch (e: Exception) {
      throw IllegalArgumentException("parsing error", e)
    }
  }

  fun parseServiceImplementationBindings(uri: URI): ServiceImplementationBindings {
    try {
      evaluator().use { evaluator ->
        return evaluator
          .evaluate(ModuleSource.uri(uri))
          .`as`(ServiceImplementationBindings::class.java)
      }
    } catch (e: Exception) {
      throw IllegalArgumentException("parsing error", e)
    }
  }
}
