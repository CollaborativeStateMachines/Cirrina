package at.ac.uibk.dps.cirrina.execution.object.guard;

import at.ac.uibk.dps.cirrina.execution.object.expression.ExpressionBuilder;

/**
 * Guard builder, used to build guard objects.
 */
public final class GuardBuilder {

  private final String expressionDescription;

  /**
   * Initializes a guard builder.
   *
   * @param expressionDescription guard expression description
   */
  private GuardBuilder(String expressionDescription) {
    this.expressionDescription = expressionDescription;
  }

  /**
   * Creates a guard builder.
   *
   * @param expressionDescription guard expression description
   * @return guard builder
   */
  public static GuardBuilder from(String expressionDescription) {
    return new GuardBuilder(expressionDescription);
  }

  /**
   * Builds the guard.
   *
   * @return the guard
   * @throws IllegalArgumentException in case the guard could not be built
   */
  public Guard build() throws IllegalArgumentException {
    return new Guard(ExpressionBuilder.from(expressionDescription).build());
  }
}
