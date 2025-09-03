package at.ac.uibk.dps.cirrina.execution.object.expression;

import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlFeatures;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.introspection.JexlPermissions;

/**
 * JEXL expression, an expression based on Apache Commons Java Expression Language (JEXL).
 *
 * @see <a href="https://commons.apache.org/proper/commons-jexl/index.html">JEXL Homepage and Documentation</a>
 */
public class JexlExpression extends Expression {

  private static final int CACHE_SIZE = 512; //TODO Determine the maximum amount of cached expressions
  private static final JexlEngine JEXL_ENGINE = getJexlEngine();
  private final JexlScript jexlScript;

  /**
   * Initializes the JEXL expression.
   *
   * @param source Source string.
   * @throws UnsupportedOperationException If the expression could not be parsed.
   */
  JexlExpression(String source) throws UnsupportedOperationException {
    super(source);
    try {
      this.jexlScript = JEXL_ENGINE.createScript(source);
    } catch (Exception e) {
      throw new UnsupportedOperationException(
        "The JEXL expression '%s' could not be parsed".formatted(source),
        e
      );
    }
  }

  /**
   * Returns the standard JEXL engine.
   *
   * @return JEXL engine.
   */
  private static JexlEngine getJexlEngine() {
    final Map<String, Object> namespaces = new HashMap<>();

    namespaces.put("math", Math.class); // Enable math methods, e.g. math:sin(x), math:min(x, y), math:random()
    namespaces.put("utility", Utility.class);

    var features = new JexlFeatures().sideEffectGlobal(false).sideEffect(true);

    return new JexlBuilder()
      .features(features)
      .cache(CACHE_SIZE)
      .namespaces(namespaces)
      .permissions(JexlPermissions.UNRESTRICTED)
      .create();
  }

  /**
   * Executes this expression, producing a value.
   *
   * @param extent Extent for resolving variables.
   * @return Result of the expression.
   * @throws UnsupportedOperationException If the expression could not be executed.
   */
  @Override
  public Object execute(Extent extent) throws UnsupportedOperationException {
    try {
      return jexlScript.execute(new ExtentJexlContext(extent));
    } catch (Exception e) {
      throw new UnsupportedOperationException(
        "The JEXL expression '%s' could not be executed".formatted(jexlScript.getSourceText()),
        e
      );
    }
  }

  /**
   * JEXL context, which has access to all variables within an Extent.
   *
   * @see Extent
   */
  private record ExtentJexlContext(Extent extent) implements JexlContext {
    @Override
    public Object get(String key) {
      return extent
        .resolve(key)
        .orElseThrow(() ->
          new NoSuchElementException(String.format("Variable not found: %s", key))
        );
    }

    @Override
    public void set(String key, Object value) {}

    @Override
    public boolean has(String key) {
      return extent.resolve(key).isPresent();
    }
  }
}
