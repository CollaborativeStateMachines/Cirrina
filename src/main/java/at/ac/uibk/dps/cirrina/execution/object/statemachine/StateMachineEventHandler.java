package at.ac.uibk.dps.cirrina.execution.object.statemachine;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;

public class StateMachineEventHandler {

  private final StateMachine stateMachine;

  private final EventHandler eventHandler;

  public StateMachineEventHandler(StateMachine stateMachine, EventHandler eventHandler) {
    this.stateMachine = stateMachine;
    this.eventHandler = eventHandler;
  }

  public void sendEvent(Event event) {
    eventHandler.sendEvent(event, stateMachine.getStateMachineInstanceId().toString());
  }
}
