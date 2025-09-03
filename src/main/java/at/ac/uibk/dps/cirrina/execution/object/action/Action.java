package at.ac.uibk.dps.cirrina.execution.object.action;

import at.ac.uibk.dps.cirrina.io.plantuml.Exportable;
import at.ac.uibk.dps.cirrina.io.plantuml.PlantUmlVisitor;

/**
 * Base action, can represent any action.
 */
public abstract class Action implements Exportable {

  /**
   * To string.
   *
   * @return String representation.
   */
  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  /**
   * Visitor accept for PlantUML exporting.
   *
   * @param visitor Visitor.
   */
  @Override
  public void accept(PlantUmlVisitor visitor) {
    visitor.visit(this);
  }
}
