package at.ac.uibk.dps.cirrina.execution.object.context;

import at.ac.uibk.dps.cirrina.csm.Csml.ContextVariableDescription;
import at.ac.uibk.dps.cirrina.execution.object.expression.ExpressionBuilder;
import java.util.Optional;

/**
 * Context variable builder, used to build context variable objects.
 */
public class ContextVariableBuilder {

  /**
   * The context variable class to build from.
   */
  private final Optional<ContextVariableDescription> contextVariableClass;

  /**
   * Name of the variable to build, has priority when set.
   */
  private Optional<String> name = Optional.empty();

  /**
   * Value of the variable to build, has priority when set.
   */
  private Optional<Object> value = Optional.empty();

  private ContextVariableBuilder() {
    this.contextVariableClass = Optional.empty();
  }

  /**
   * Initializes a context variable builder.
   *
   * @param contextVariableDescription Context variable class.
   */
  private ContextVariableBuilder(ContextVariableDescription contextVariableDescription) {
    this.contextVariableClass = Optional.of(contextVariableDescription);
  }

  /**
   * Initializes a context variable builder.
   */
  public static ContextVariableBuilder from() {
    return new ContextVariableBuilder();
  }

  /**
   * Creates a context variable builder.
   *
   * @param contextVariableDescription Context variable class.
   * @return Context variable builder.
   */
  public static ContextVariableBuilder from(ContextVariableDescription contextVariableDescription) {
    return new ContextVariableBuilder(contextVariableDescription);
  }

  /**
   * Specifies the name of the variable to build.
   *
   * @param name Name
   * @return This builder.
   */
  public ContextVariableBuilder name(String name) {
    this.name = Optional.of(name);
    return this;
  }

  /**
   * Specifies the value of the variable to build.
   *
   * @param value Value
   * @return This builder.
   */
  public ContextVariableBuilder value(Object value) {
    this.value = Optional.of(value);
    return this;
  }

  /**
   * Builds the context variable.
   *
   * @return The built context variable.
   */
  public ContextVariable build() {
    // If the name and value are set, build based on them
    if (name.isPresent() && value.isPresent()) {
      return new ContextVariable(name.get(), value.get());
    }
    // If the variable comes from a context variable class, build based on the class
    else if (contextVariableClass.isPresent()) {
      var lazyVariable = ExpressionBuilder.from(contextVariableClass.get().getValue()).build();
      return new ContextVariable(contextVariableClass.get().getName(), lazyVariable);
    }
    // If neither name and value nor context variable class is provided, throw an exception or handle it according to your use case
    else {
      throw new IllegalStateException(
        "Name and value or context variable class must be provided to build the context variable."
      );
    }
  }
}
