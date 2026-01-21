package at.ac.uibk.dps.cirrina.execution.object.action;

/**
 * Timeout reset action object.
 */
public final class TimeoutResetAction extends Action {

  /**
   * The name of the timeout action to stop.
   */
  private final String action;

  /**
   * Initializes this timeout reset action object.
   *
   * @param parameters Parameters.
   */
  TimeoutResetAction(Parameters parameters) {
    this.action = parameters.action;
  }

  /**
   * Returns the name of the timeout action to reset.
   *
   * @return Timeout action name.
   */
  public String getAction() {
    return action;
  }

  public record Parameters(String action) {}
}
