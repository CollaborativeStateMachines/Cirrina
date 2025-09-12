package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class RaiseAction extends Action implements EventRaisingAction {

  private final Event event;

  RaiseAction(Parameters parameters) {
    this.event = parameters.event();
  }

  public Event getEvent() {
    return event;
  }

  @Override
  @NotNull
  public List<Event> raises() {
    return List.of(getEvent());
  }

  public record Parameters(Event event) {}
}
