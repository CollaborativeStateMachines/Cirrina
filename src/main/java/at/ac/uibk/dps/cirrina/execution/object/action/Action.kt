package at.ac.uibk.dps.cirrina.execution.object.action;

/**
 * Base action, can represent any action.
 */
public abstract class Action {

  /**
   * To string.
   *
   * @return String representation.
   */
  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
