package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.csml.description.Csml.ContextVariableReferenceDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Invoke action, invokes a service type.
 */
public final class InvokeAction extends Action implements EventRaisingAction {

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

  @Override
  @NotNull
  public List<Event> raises() {
    return done;
  }

  public record Parameters(
    String serviceType,
    boolean isLocal,
    List<ContextVariable> input,
    List<Event> done,
    List<ContextVariableReferenceDescription> output
  ) {}
}
