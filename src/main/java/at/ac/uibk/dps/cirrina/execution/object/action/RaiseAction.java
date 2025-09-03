package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;

public final class RaiseAction extends Action {

  private final Event event;

  RaiseAction(Parameters parameters) {
    this.event = parameters.event();
  }

  public Event getEvent() {
    return event;
  }

  public record Parameters(Event event) {}
}
