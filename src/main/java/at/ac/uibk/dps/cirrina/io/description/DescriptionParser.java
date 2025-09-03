package at.ac.uibk.dps.cirrina.io.description;

import org.pkl.config.java.ConfigEvaluator;
import org.pkl.core.ModuleSource;

/**
 * Generic DescriptionParser. Provides functionality to parse JSON data into an object of the parser's value type.
 */
public class DescriptionParser<T> {

  /**
   * DescriptionParser value type
   */
  private final Class<T> valueType;

  /**
   * Initializes the parser, provided the value type.
   *
   * @param valueType DescriptionParser value type.
   */
  public DescriptionParser(Class<T> valueType) {
    this.valueType = valueType;
  }

  /**
   * Parse a Pkl string. Returns an instance of the parsers value type. Any errors will result in a CirrinaException being thrown. Errors
   * could be syntax errors as well as validation errors such as missing fields.
   *
   * @param pkl Pkl description.
   * @return Collaborative state machine model.
   * @throws IllegalArgumentException If an error occurs during parsing or validation.
   */
  public T parse(String pkl) throws IllegalArgumentException {
    try (var evaluator = ConfigEvaluator.preconfigured()) {
      return evaluator.evaluate(ModuleSource.text(pkl)).as(valueType);
    } catch (Exception e) {
      throw new IllegalArgumentException("Parsing error", e);
    }
  }
}
