package at.ac.uibk.dps.cirrina.classes.transition;

import at.ac.uibk.dps.cirrina.classes.state.StateClass;
import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionGraph;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionGraphBuilder;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.guard.Guard;
import at.ac.uibk.dps.cirrina.io.plantuml.Exportable;
import at.ac.uibk.dps.cirrina.io.plantuml.PlantUmlVisitor;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import io.opentelemetry.api.trace.Span;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jgrapht.graph.DefaultEdge;

/**
 * TransitionClass, represents a transition that can be 'taken' between two states in a state machine.
 * <p>
 * The base transition is not event-triggered meaning that it is 'taken' whenever the guards all produce a true value. Actions can be
 * executed during a transition.
 * <p>
 * The else property of the transition represents the target state whenever the transition is not taken (at least one of the guards do not
 * produce a true value).
 */
public class TransitionClass extends DefaultEdge implements Exportable {

  /**
   * The name of the target state.
   */
  private final @Nullable String targetStateName;

  /**
   * Collection of guards.
   */
  private final List<Guard> guards;

  /**
   * Else target state name.
   */
  private final @Nullable String elseTargetStateName;

  /**
   * Action graph.
   */
  private final ActionGraph actionGraph;

  /**
   * Initializes this transition object.
   *
   * @param targetStateName     Name of the target state.
   * @param elseTargetStateName Name of the else state.
   * @param guards              List of guards.
   * @param actions             List of actions.
   */
  TransitionClass(@Nullable String targetStateName, @Nullable String elseTargetStateName, List<Guard> guards, List<Action> actions) {
    this.targetStateName = targetStateName;
    this.guards = guards;
    this.elseTargetStateName = elseTargetStateName;

    this.actionGraph = ActionGraphBuilder.from(actions).build();
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
   * Evaluate all guards of this transition. A return value of true indicates that all guard conditions produced true, a return value of
   * false indicates that at least one guard condition returned false.
   *
   * @param extent Extent describing variables in scope.
   * @return True if the transition can be taken based on the guards, otherwise false.
   * @throws UnsupportedOperationException If the transition could not be evaluated.
   */
  public boolean evaluate(Extent extent, TracingAttributes tracingAttributes, Span span) throws UnsupportedOperationException {
    try {
      for (var guard : guards) {
        if (!guard.evaluate(extent, tracingAttributes, span)) {
          return false;
        }
      }
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      throw new UnsupportedOperationException("Transition could not be evaluated", e);
    }

    return true;
  }

  /**
   * Returns the name of the target state.
   *
   * @return Name of the target state.
   */
  @Override
  public StateClass getSource() {
    return (StateClass) super.getSource();
  }

  /**
   * Returns the name of the target state.
   *
   * @return Name of the target state.
   */
  @Override
  public StateClass getTarget() {
    return (StateClass) super.getTarget();
  }

  /**
   * Returns the guards.
   *
   * @return Guards.
   */
  public List<Guard> getGuards() {
    return guards;
  }

  /**
   * Returns the action graph.
   *
   * @return Action graph.
   */
  public ActionGraph getActionGraph() {
    return actionGraph;
  }

  /**
   * Returns the name of the target state.
   *
   * @return Name of the target state.
   */
  public Optional<String> getTargetStateName() {
    return Optional.ofNullable(targetStateName);
  }

  /**
   * Returns the name of the else state.
   *
   * @return Name of the else state.
   */
  public Optional<String> getElse() {
    return Optional.ofNullable(elseTargetStateName);
  }

  /**
   * Returns actions contained in the action graph by type.
   *
   * @param type Type to return.
   * @param <T>  Type to return.
   * @return Actions of type.
   */
  public <T> List<T> getActionsOfType(Class<T> type) {
    return Stream.of(actionGraph)
        .map(actionGraph -> actionGraph.getActionsOfType(type))
        .flatMap(Collection::stream)
        .toList();
  }
}
