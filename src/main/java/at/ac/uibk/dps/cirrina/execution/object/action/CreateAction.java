package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;

/**
 * Create action, creates a new variable.
 */
public final class CreateAction extends Action {

  /**
   * The variable to create, is an expression. The name of the variable will be retrieved through evaluating the expression.
   */
  private final ContextVariable variable;

  /**
   * Indicates whether this variable should be created persistently.
   */
  private final boolean isPersistent;

  /**
   * Initializes this create action.
   *
   * @param parameters Initialization parameters.
   * @throws IllegalArgumentException In case compilation of the expressions fails.
   */
  CreateAction(Parameters parameters) {
    this.variable = parameters.variable();
    this.isPersistent = parameters.isPersistent();
  }

  public ContextVariable getVariable() {
    return variable;
  }

  public boolean isPersistent() {
    return isPersistent;
  }

  public record Parameters(ContextVariable variable, boolean isPersistent) {}
}
