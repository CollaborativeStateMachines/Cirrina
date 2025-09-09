package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.AssignAction;
import at.ac.uibk.dps.cirrina.execution.object.action.CreateAction;
import at.ac.uibk.dps.cirrina.execution.object.action.InvokeAction;
import at.ac.uibk.dps.cirrina.execution.object.action.MatchAction;
import at.ac.uibk.dps.cirrina.execution.object.action.RaiseAction;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutResetAction;

public class CommandFactory {

  private ExecutionContext executionContext;

  public CommandFactory(ExecutionContext executionContext) {
    this.executionContext = executionContext;
  }

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
      case TimeoutResetAction timeoutResetAction -> {
        return new ActionTimeoutResetCommand(executionContext, timeoutResetAction);
      }
      default -> throw new IllegalArgumentException("Unexpected action");
    }
  }
}
