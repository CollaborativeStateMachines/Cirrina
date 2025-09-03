package at.ac.uibk.dps.cirrina.classes.transition;

import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.guard.Guard;
import at.ac.uibk.dps.cirrina.io.plantuml.Exportable;
import at.ac.uibk.dps.cirrina.io.plantuml.PlantUmlVisitor;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * On transition, represents a transition that is taken based on an event.
 */
public final class OnTransitionClass extends TransitionClass implements Exportable {

  /**
   * Name of the event that triggered the transition.
   */
  private final String eventName;

  /**
   * Initializes this on transition class.
   *
   * @param targetStateName     Target state name.
   * @param elseTargetStateName Else target state name, can be null in case none is declared.
   * @param guards
   * @param actions
   * @param eventName
   */
  OnTransitionClass(
    String targetStateName,
    @Nullable String elseTargetStateName,
    List<Guard> guards,
    List<Action> actions,
    String eventName
  ) {
    super(targetStateName, elseTargetStateName, guards, actions);
    this.eventName = eventName;
  }

  /**
   * Return a string representation.
   *
   * @return String representation.
   */
  @Override
  public String toString() {
    return eventName;
  }

  /**
   * PlantUML visitor accept.
   *
   * @param visitor PlantUML visitor.
   */
  @Override
  public void accept(PlantUmlVisitor visitor) {
    visitor.visit(this);
  }

  /**
   * Returns the event name.
   *
   * @return Event name.
   */
  public String getEventName() {
    return eventName;
  }
}
