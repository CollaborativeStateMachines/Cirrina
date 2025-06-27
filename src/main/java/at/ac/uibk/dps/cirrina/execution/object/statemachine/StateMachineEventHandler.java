package at.ac.uibk.dps.cirrina.execution.object.statemachine;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.observability.tracing.MethodName;
import at.ac.uibk.dps.cirrina.observability.tracing.Trace;
import java.io.IOException;

public class StateMachineEventHandler {

  private final StateMachine stateMachine;

  private final EventHandler eventHandler;

  public StateMachineEventHandler(StateMachine stateMachine, EventHandler eventHandler) {
    this.stateMachine = stateMachine;
    this.eventHandler = eventHandler;
  }

  @Trace(name = MethodName.SEND_EVENT)
  public void sendEvent(Event event) throws IOException {
    eventHandler.sendEvent(event, stateMachine.getStateMachineInstanceId().toString());
  }
}
