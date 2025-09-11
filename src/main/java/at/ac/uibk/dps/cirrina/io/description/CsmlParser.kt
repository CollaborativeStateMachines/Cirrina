package at.ac.uibk.dps.cirrina.io.description

import at.ac.uibk.dps.cirrina.csml.description.Csml
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource

/** CsmlParser, used to parse a Pkl file into a Csml object. */
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
      ConfigEvaluator.preconfigured().use { evaluator ->
        return evaluator.evaluate(ModuleSource.file(mainModulePath)).`as`(Csml::class.java)
      }
    } catch (e: Exception) {
      throw IllegalArgumentException("Parsing error", e)
    }
  }
}
