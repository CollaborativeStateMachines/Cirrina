package at.ac.uibk.dps.cirrina.execution.object.context;

import at.ac.uibk.dps.cirrina.execution.object.expression.Expression;
import java.util.Optional;

/**
 * Context variable builder, used to build context variable objects.
 */
public class ContextVariableBuilder {

  private Optional<String> name = Optional.empty();

  private Optional<Object> value = Optional.empty();

  private Optional<Expression> expression = Optional.empty();

  /**
   * Initializes a context variable builder.
   */
  public static ContextVariableBuilder empty() {
    return new ContextVariableBuilder();
  }

  /**
   * Specifies the name of the variable to build.
   *
   * @param name name
   * @return this builder
   */
  public ContextVariableBuilder name(String name) {
    this.name = Optional.of(name);
    return this;
  }

  /**
   * Specifies the value of the variable to build.
   *
   * @param value value
   * @return this builder
   */
  public ContextVariableBuilder value(Object value) {
    this.value = Optional.of(value);
    return this;
  }

  /**
   * Specifies the expression of the variable to build.
   *
   * @param expression expression
   * @return this builder
   */
  public ContextVariableBuilder expression(Expression expression) {
    this.expression = Optional.of(expression);
    return this;
  }

  /**
   * Builds the context variable.
   *
   * @return the built context variable
   */
  public ContextVariable build() {
    // If the variable has a name and concrete value set, build a context variable with the name and the value object
    if (name.isPresent() && value.isPresent()) {
      return new ContextVariable(name.get(), value.get());
    }
    // Otherwise, build a context variable where the value comes from the provided expression
    else if (name.isPresent() && expression.isPresent()) {
      return new ContextVariable(name.get(), expression);
    }
    // If neither name and value nor context variable class is provided, throw an exception
    else {
      throw new IllegalStateException(
        "Name and value or context variable class must be provided to build the context variable."
      );
    }
  }
}
