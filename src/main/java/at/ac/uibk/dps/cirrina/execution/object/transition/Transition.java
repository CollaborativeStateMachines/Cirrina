package at.ac.uibk.dps.cirrina.execution.object.transition;

import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass;
import at.ac.uibk.dps.cirrina.execution.command.ActionCommand;
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jgrapht.traverse.TopologicalOrderIterator;

public final class Transition {

  private final TransitionClass transitionClass;

  private final boolean isElse;

  public Transition(TransitionClass transitionClass, boolean isElse) {
    this.transitionClass = transitionClass;
    this.isElse = isElse;

    assert !isElse || transitionClass.getElse().isPresent();
  }

  public boolean isInternalTransition() {
    return transitionClass.getTargetStateName().isEmpty();
  }

  public TransitionClass getTransitionObject() {
    return transitionClass;
  }

  public Optional<String> getTargetStateName() {
    return isElse ? transitionClass.getElse() : transitionClass.getTargetStateName();
  }

  public List<ActionCommand> getActionCommands(CommandFactory commandFactory, TracingAttributes tracingAttributes) {
    List<ActionCommand> actionCommands = new ArrayList<>();

    new TopologicalOrderIterator<>(transitionClass.getActionGraph()).forEachRemaining(
        action -> actionCommands.add(commandFactory.createActionCommand(action, tracingAttributes, null)));

    return actionCommands;
  }

  public boolean isElse() {
    return isElse;
  }
}
