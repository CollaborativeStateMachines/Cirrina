package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutResetAction;
import java.util.List;

public final class ActionTimeoutResetCommand extends ActionCommand {

  private final TimeoutResetAction timeoutResetAction;

  ActionTimeoutResetCommand(
    ExecutionContext executionContext,
    TimeoutResetAction timeoutResetAction
  ) {
    super(executionContext);
    this.timeoutResetAction = timeoutResetAction;
  }

  @Override
  public List<ActionCommand> execute() throws UnsupportedOperationException {
    // Handled in StateMachine
    return List.of();
  }

  public TimeoutResetAction getTimeoutResetAction() {
    return timeoutResetAction;
  }
}
