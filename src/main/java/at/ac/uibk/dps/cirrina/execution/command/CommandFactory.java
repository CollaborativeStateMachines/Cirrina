package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.AssignAction;
import at.ac.uibk.dps.cirrina.execution.object.action.CreateAction;
import at.ac.uibk.dps.cirrina.execution.object.action.InvokeAction;
import at.ac.uibk.dps.cirrina.execution.object.action.MatchAction;
import at.ac.uibk.dps.cirrina.execution.object.action.RaiseAction;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import at.ac.uibk.dps.cirrina.observability.MethodName;
import at.ac.uibk.dps.cirrina.observability.logging.Log;
import at.ac.uibk.dps.cirrina.observability.tracing.Trace;

public class CommandFactory {

  private ExecutionContext executionContext;

  public CommandFactory(ExecutionContext executionContext) {
    this.executionContext = executionContext;
  }

  @Trace(name = MethodName.CREATE_ACTION_COMMAND)
  @Log(name = MethodName.CREATE_ACTION_COMMAND)
  public ActionCommand createActionCommand(Action action) {
    switch (action) {
      case AssignAction assignAction -> {
        return new ActionAssignCommand(executionContext, assignAction);
      }
      case CreateAction createAction -> {
        return new ActionCreateCommand(executionContext, createAction);
      }
      case InvokeAction invokeAction -> {
        return new ActionInvokeCommand(executionContext, invokeAction);
      }
      case MatchAction matchAction -> {
        return new ActionMatchCommand(executionContext, matchAction);
      }
      case RaiseAction raiseAction -> {
        return new ActionRaiseCommand(executionContext, raiseAction);
      }
      case TimeoutAction timeoutAction -> {
        return new ActionTimeoutCommand(executionContext, timeoutAction);
      }
      default -> throw new IllegalArgumentException("Unexpected action");
    }
  }
}
