package at.ac.uibk.dps.cirrina.tracing;

public class TracingAttributes {

  String stateMachineId;

  String stateMachineName;

  String parentStateMachineId;

  String parentStateMachineName;

  public String getStateMachineId() {
    return stateMachineId;
  }

  public void setStateMachineId(String stateMachineId) {
    this.stateMachineId = stateMachineId;
  }

  public String getStateMachineName() {
    return stateMachineName;
  }

  public void setStateMachineName(String stateMachineName) {
    this.stateMachineName = stateMachineName;
  }

  public String getParentStateMachineId() {
    return parentStateMachineId;
  }

  public void setParentStateMachineId(String parentStateMachineId) {
    parentStateMachineId = parentStateMachineId;
  }

  public String getParentStateMachineName() {
    return parentStateMachineName;
  }

  public void setParentStateMachineName(String parentStateMachineName) {
    parentStateMachineName = parentStateMachineName;
  }

  public TracingAttributes(String stateMachineId, String stateMachineName, String parentStateMachineId, String parentStateMachineName) {
    this.stateMachineId = stateMachineId;
    this.stateMachineName = stateMachineName;
    this.parentStateMachineId = parentStateMachineId;
    this.parentStateMachineName = parentStateMachineName;
  }

  @Override
  public String toString() {
    return "TracingAttributes:" +
        "StateMachineId = '" + stateMachineId + '\'' +
        ", StateMachineName = '" + stateMachineName + '\'' +
        ", ParentStateMachineId = '" + parentStateMachineId + '\'' +
        ", ParentStateMachineName = '" + parentStateMachineName + '\'';
  }
}
