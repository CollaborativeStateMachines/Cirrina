package at.ac.uibk.dps.cirrina.execution.object.context;

import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;
import java.util.Objects;

/**
 * Context variable, contained within a context.
 */
public record ContextVariable(
  /**
   * Name of the context variable.
   */
  String name,

  /**
   * Value of the context variable. Can be an arbitrary value or must be an expression in case this context variable is lazy.
   */
  Object value,

  /**
   * Laziness of the context variable, if true, the value must be an expression and the value of the context variable is acquired through executing the expression.
   */
  boolean isLazy
) {
  public ContextVariable {
    Objects.requireNonNull(name, "Name cannot be null");
  }

  /**
   * Initializes a lazy context variable.
   *
   * @param name  Name of the variable.
   * @param value Value expression of the variable.
   */
  public ContextVariable(String name, Expression value) {
    this(name, value, true);
  }

  /**
   * Initializes a non-lazy context variable.
   *
   * @param name  Name of the variable.
   * @param value Value of the variable.
   */
  public ContextVariable(String name, Object value) {
    this(name, value, false);
  }

  /**
   * Evaluate this variable.
   *
   * @param extent Extent.
   * @return Evaluated variable.
   * @throws UnsupportedOperationException If the variable could not be evaluated (e.g. its expression could not be executed).
   */
  public ContextVariable evaluate(Extent extent) throws UnsupportedOperationException {
    if (isLazy) {
      final var expression = value;

      assert expression instanceof Expression;

      try {
        return new ContextVariable(name, ((Expression) expression).execute(extent));
      } catch (UnsupportedOperationException e) {
        throw new UnsupportedOperationException(
          "Could not evaluate variable '%s'".formatted(name),
          e
        );
      }
    } else {
      return this;
    }
  }

  @Override
  public String toString() {
    return "{" + name + " = " + value.toString() + "}";
  }
}
