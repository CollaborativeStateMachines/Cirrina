package at.ac.uibk.dps.cirrina.io.description;

import at.ac.uibk.dps.cirrina.csml.description.Csml;
import org.pkl.config.java.ConfigEvaluator;
import org.pkl.core.ModuleSource;

/**
 * CsmlParser, used to parse a Pkl file into a Csml object.
 */
public class CsmlParser {

  /**
   * Parse a Pkl module at a path.
   *
   * Returns an instance of Csml upon success.
   *
   * Any errors will result in a IllegalArgumentException being thrown.
   *
   * @param modulePath Pkl module path.
   * @return Csml model.
   * @throws IllegalArgumentException If an error occurs.
   */
  public static Csml parse(String modulePath) throws IllegalArgumentException {
    try (var evaluator = ConfigEvaluator.preconfigured()) {
      return evaluator.evaluate(ModuleSource.modulePath(modulePath)).as(Csml.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Parsing error", e);
    }
  }
}
