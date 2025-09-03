package at.ac.uibk.dps.cirrina.io.plantuml;

public interface Exportable {
  void accept(PlantUmlVisitor visitor);
}
