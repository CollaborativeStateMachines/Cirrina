package at.ac.uibk.dps.cirrina.execution.object.event;

public interface EventListener {
  boolean onReceiveEvent(Event event);
}
