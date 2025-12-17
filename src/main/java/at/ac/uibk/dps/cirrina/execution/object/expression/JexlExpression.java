package at.ac.uibk.dps.cirrina.execution.object.expression;

import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.jexl3.JexlArithmetic;
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
    namespaces.put("std", Stdlib.class);

    var features = new JexlFeatures().sideEffectGlobal(false).sideEffect(false);

    return new JexlBuilder()
      .arithmetic(new CsmlArithmetic(true))
      .features(features)
      .cache(CACHE_SIZE)
      .namespaces(namespaces)
      .permissions(JexlPermissions.UNRESTRICTED)
      .strict(true)
      .silent(false)
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

  /**
   * CsmlArithmetic extends JexlArithmetic to provide custom operator overloading
   * for collections, arrays, and maps in JEXL expressions.
   *
   * <p>Supports:
   * <ul>
   *   <li>List + List / Array → List</li>
   *   <li>Set + Set / Array → LinkedHashSet</li>
   *   <li>Array + Array / List / Set → Object[]</li>
   *   <li>Map + Map → merged map (right overwrites left)</li>
   *   <li>Corresponding subtraction (-) removes elements or keys</li>
   * </ul>
   *
   * <p>All operations are side-effect-free.
   */
  private static class CsmlArithmetic extends JexlArithmetic {

    /**
     * Constructs a CsmlArithmetic instance with the specified strict mode.
     *
     * @param strict if true, the arithmetic engine runs in strict mode,
     *               where it throws exceptions for errors
     */
    public CsmlArithmetic(boolean strict) {
      super(strict);
    }

    /**
     * Adds two objects together
     *
     * @param left the first operand
     * @param right the second operand
     * @return the result of the addition
     */
    @Override
    public Object add(Object left, Object right) {
      // Left is a list: concatenate left and right, result is a List
      if (left instanceof List<?>) {
        return Stream.concat(toStream(left), toStream(right)).collect(Collectors.toList());
      }

      // Left is a set: concatenate left and right, result is a LinkedHashSet to preserve uniqueness
      if (left instanceof Set<?>) {
        return Stream.concat(toStream(left), toStream(right)).collect(
          Collectors.toCollection(LinkedHashSet::new)
        );
      }

      // Left is an array: concatenate left and right streams, result is an Object[]
      if (left != null && left.getClass().isArray()) {
        return Stream.concat(toStream(left), toStream(right)).toArray();
      }

      // Left and right are maps: merge entries, right-hand side overwrites left-hand side keys
      if (left instanceof Map<?, ?> lm && right instanceof Map<?, ?> rm) {
        return Stream.concat(lm.entrySet().stream(), rm.entrySet().stream()).collect(
          Collectors.toMap(Entry::getKey, Entry::getValue, (oldV, newV) -> newV, HashMap::new)
        );
      }

      // Delegate to default arithmetic
      return super.add(left, right);
    }

    @Override
    public Object subtract(Object left, Object right) {
      // Left is List/Set/Array: remove elements present in right
      if (isIterableLike(left) && isIterableLike(right)) {
        Set<Object> rightSet = toStream(right).collect(Collectors.toSet());
        Stream<Object> leftStream = toStream(left).filter(e -> !rightSet.contains(e));

        if (left instanceof List<?>) {
          return leftStream.collect(Collectors.toList());
        }

        if (left instanceof Set<?>) {
          return leftStream.collect(Collectors.toCollection(LinkedHashSet::new));
        }

        if (left.getClass().isArray()) {
          Class<?> componentType = left.getClass().getComponentType();
          return leftStream.toArray(size -> (Object[]) Array.newInstance(componentType, size));
        }
      }

      // Left is Map, right is Map or iterable of keys
      if (left instanceof Map<?, ?> lm) {
        Set<Object> keysToRemove = Stream.of(right)
          .flatMap(r -> {
            if (r instanceof Map<?, ?> rm) {
              return rm.keySet().stream();
            } else if (isIterableLike(r)) {
              return toStream(r);
            } else {
              return Stream.of(r);
            }
          })
          .collect(Collectors.toSet());

        return lm
          .entrySet()
          .stream()
          .filter(e -> !keysToRemove.contains(e.getKey()))
          .collect(
            Collectors.toMap(
              Entry::getKey,
              Entry::getValue,
              (a, b) -> b,
              () -> new HashMap<>(lm.size())
            )
          );
      }

      // Delegate to default arithmetic
      return super.subtract(left, right);
    }

    private static boolean isIterableLike(Object o) {
      return o instanceof Collection<?> || (o != null && o.getClass().isArray());
    }

    private static Stream<Object> toStream(Object o) {
      if (o instanceof Collection<?> c) {
        return c.stream().map(x -> x);
      }
      if (o != null && o.getClass().isArray()) {
        return IntStream.range(0, Array.getLength(o)).mapToObj(i -> Array.get(o, i));
      }
      return Stream.empty();
    }
  }
}
