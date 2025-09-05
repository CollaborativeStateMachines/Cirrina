package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass;

public class SpawnAction extends Action {

  private final StateMachineClass stateMachine;

  SpawnAction(Parameters parameters) {
    this.stateMachine = parameters.stateMachine();
    System.out.println("SpawnAction created with stateMachine: " + stateMachine.getName());
  }

  public StateMachineClass getStateMachine() {
    return stateMachine;
  }

  public record Parameters(StateMachineClass stateMachine) {}
}
