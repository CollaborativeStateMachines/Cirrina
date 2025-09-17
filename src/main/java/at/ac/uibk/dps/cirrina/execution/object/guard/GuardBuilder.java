package at.ac.uibk.dps.cirrina.execution.object.guard;

import at.ac.uibk.dps.cirrina.csm.Csml.GuardDescription;
import at.ac.uibk.dps.cirrina.execution.object.expression.ExpressionBuilder;

/**
 * Guard builder, used to build guard objects.
 */
public final class GuardBuilder {

  /**
   * The guard class to build from.
   */
  private final GuardDescription guardClass;

  /**
   * Initializes a guard builder.
   *
   * @param guardClass Guard class.
   */
  private GuardBuilder(GuardDescription guardClass) {
    this.guardClass = guardClass;
  }

  /**
   * Creates a guard builder.
   *
   * @param guardClass Guard class.
   * @return Guard builder.
   */
  public static GuardBuilder from(GuardDescription guardClass) {
    return new GuardBuilder(guardClass);
  }

  /**
   * Builds the guard.
   *
   * @return The built guard.
   * @throws IllegalArgumentException In case the guard could not be built.
   */
  public Guard build() throws IllegalArgumentException {
    return new Guard(ExpressionBuilder.from(guardClass.getExpression()).build());
  }
}
