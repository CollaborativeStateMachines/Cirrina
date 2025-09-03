package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.ContextVariableReferenceDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import java.util.List;

/**
 * Invoke action, invokes a service type.
 */
public final class InvokeAction extends Action {

  private final String serviceType;

  private final boolean isLocal;

  private final List<ContextVariable> input;

  private final List<Event> done;

  private final List<ContextVariableReferenceDescription> output;

  InvokeAction(Parameters parameters) {
    this.serviceType = parameters.serviceType();
    this.isLocal = parameters.isLocal();
    this.input = parameters.input();
    this.done = parameters.done();
    this.output = parameters.output();
  }

  public String getServiceType() {
    return serviceType;
  }

  public boolean isLocal() {
    return isLocal;
  }

  public List<ContextVariable> getInput() {
    return input;
  }

  public List<Event> getDone() {
    return done;
  }

  public List<ContextVariableReferenceDescription> getOutput() {
    return output;
  }

  public record Parameters(
    String serviceType,
    boolean isLocal,
    List<ContextVariable> input,
    List<Event> done,
    List<ContextVariableReferenceDescription> output
  ) {}
}
