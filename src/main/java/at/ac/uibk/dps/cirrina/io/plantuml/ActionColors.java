package at.ac.uibk.dps.cirrina.io.plantuml;

import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.EvalAction;
import at.ac.uibk.dps.cirrina.execution.object.action.InvokeAction;
import at.ac.uibk.dps.cirrina.execution.object.action.MatchAction;
import at.ac.uibk.dps.cirrina.execution.object.action.RaiseAction;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutResetAction;

public class ActionColors {

  private ActionColors() {}

  public static String getActionColor(Action action) {
    return switch (action) {
      case EvalAction evalAction -> "#27AE60"; // Green
      case InvokeAction invokeAction -> "#1F618D"; // Blue
      case MatchAction matchAction -> "#1ABC9C"; // Turquoise
      case RaiseAction raiseAction -> "#9B59B6"; // Purple
      case TimeoutAction timeoutAction -> "#34495E"; // Dark Gray
      case TimeoutResetAction timeoutResetAction -> "#34495E"; // Dark Gray
      default -> "#000000"; // Black
    };
  }
}
