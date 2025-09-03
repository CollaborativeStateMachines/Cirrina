package at.ac.uibk.dps.cirrina.io.plantuml;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass;
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass;
import java.util.ArrayList;
import java.util.List;

public class PlantUmlExporter {

  private final List<StateMachineClass> stateMachineClasses = new ArrayList<>();

  public static PlantUmlExporter from(
    CollaborativeStateMachineClass collaborativeStateMachineClass
  ) {
    var exporter = new PlantUmlExporter();
    collaborativeStateMachineClass.getStateMachineClasses().forEach(exporter::withStateMachine);
    return exporter;
  }

  public static PlantUmlExporter from(StateMachineClass stateMachineClass) {
    return new PlantUmlExporter().withStateMachine(stateMachineClass);
  }

  public PlantUmlExporter withStateMachine(StateMachineClass stateMachineClass) {
    stateMachineClasses.add(stateMachineClass);
    return this;
  }

  public String getPlantUml() {
    var builder = new StringBuilder();
    builder.append("@startuml\n");
    PlantUmlVisitor visitor = new PlantUmlVisitor();
    stateMachineClasses.forEach(sm -> sm.accept(visitor));
    builder.append(visitor.getPlantUml());
    builder.append("@enduml");
    return builder.toString();
  }
}
