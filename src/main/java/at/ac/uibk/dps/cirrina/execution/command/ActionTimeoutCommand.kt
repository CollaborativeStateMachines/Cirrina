package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import java.util.List;

public final class ActionTimeoutCommand extends ActionCommand {

  private final TimeoutAction timeoutAction;

  ActionTimeoutCommand(ExecutionContext executionContext, TimeoutAction timeoutAction) {
    super(executionContext);
    this.timeoutAction = timeoutAction;
  }

  @Override
  public List<ActionCommand> execute() throws UnsupportedOperationException {
    final var commandFactory = new CommandFactory(executionContext);

    return List.of(commandFactory.createActionCommand(timeoutAction.getAction()));
  }
}
