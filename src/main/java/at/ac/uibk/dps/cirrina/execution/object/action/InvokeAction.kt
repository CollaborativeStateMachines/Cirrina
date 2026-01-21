package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.csm.Csml.InvocationMode;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Invoke action, invokes a service type.
 */
public final class InvokeAction extends Action implements EventRaisingAction {

  private final String serviceType;

  private final InvocationMode mode;

  private final List<ContextVariable> input;

  private final List<Event> done;

  InvokeAction(Parameters parameters) {
    this.serviceType = parameters.serviceType();
    this.mode = parameters.mode();
    this.input = parameters.input();
    this.done = parameters.done();
  }

  public String getServiceType() {
    return serviceType;
  }

  public InvocationMode getMode() {
    return mode;
  }

  public List<ContextVariable> getInput() {
    return input;
  }

  public List<Event> getDone() {
    return done;
  }

  @Override
  @NotNull
  public List<Event> raises() {
    return done;
  }

  public record Parameters(
    String serviceType,
    InvocationMode mode,
    List<ContextVariable> input,
    List<Event> done
  ) {}
}
