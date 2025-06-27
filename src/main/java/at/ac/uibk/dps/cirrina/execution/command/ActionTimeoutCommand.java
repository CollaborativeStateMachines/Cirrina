package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import at.ac.uibk.dps.cirrina.observability.tracing.MethodName;
import at.ac.uibk.dps.cirrina.observability.tracing.Trace;
import java.util.List;

public final class ActionTimeoutCommand extends ActionCommand {

  private final TimeoutAction timeoutAction;

  ActionTimeoutCommand(ExecutionContext executionContext, TimeoutAction timeoutAction) {
    super(executionContext);

    this.timeoutAction = timeoutAction;
  }

  @Trace(name = MethodName.EXECUTE_ACTION)
  @Override
  public List<ActionCommand> execute() throws UnsupportedOperationException {
    final var commandFactory = new CommandFactory(executionContext);

    return List.of(commandFactory.createActionCommand(timeoutAction.getAction()));
  }
}
