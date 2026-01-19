package at.ac.uibk.dps.cirrina.execution.object.state;

import at.ac.uibk.dps.cirrina.classes.state.StateClass;
import at.ac.uibk.dps.cirrina.execution.command.ActionCommand;
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory;
import at.ac.uibk.dps.cirrina.execution.command.Scope;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextBuilder;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.statemachine.StateMachine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jgrapht.traverse.TopologicalOrderIterator;

public final class State implements Scope {

  private final Context staticContext;

  private final StateClass stateClassObject;

  private final StateMachine parent;

  public State(StateClass stateClassObject, StateMachine parent) {
    // Build the static context
    try {
      staticContext = Optional.ofNullable(stateClassObject.getStaticContextDescription())
        .map(ContextBuilder::from)
        .orElseGet(ContextBuilder::empty)
        .inMemoryContext(true)
        .build();
    } catch (IOException ignored) {
      throw new IllegalStateException(); // This should not happen
    }

    this.stateClassObject = stateClassObject;
    this.parent = parent;
  }

  @Override
  public Extent getExtent() {
    return parent.getExtent().extend(staticContext);
  }

  @Override
  public String getId() {
    return parent.getId();
  }

  public StateClass getStateObject() {
    return stateClassObject;
  }

  public List<ActionCommand> getEntryActionCommands(CommandFactory commandFactory) {
    List<ActionCommand> actionCommands = new ArrayList<>();

    new TopologicalOrderIterator<>(stateClassObject.getEntryActionGraph()).forEachRemaining(
      action -> actionCommands.add(commandFactory.createActionCommand(action))
    );

    return actionCommands;
  }

  public List<ActionCommand> getWhileActionCommands(CommandFactory commandFactory) {
    List<ActionCommand> actionCommands = new ArrayList<>();

    new TopologicalOrderIterator<>(stateClassObject.getWhileActionGraph()).forEachRemaining(
      action -> actionCommands.add(commandFactory.createActionCommand(action))
    );

    return actionCommands;
  }

  public List<ActionCommand> getExitActionCommands(CommandFactory commandFactory) {
    List<ActionCommand> actionCommands = new ArrayList<>();

    new TopologicalOrderIterator<>(stateClassObject.getExitActionGraph()).forEachRemaining(action ->
      actionCommands.add(commandFactory.createActionCommand(action))
    );

    return actionCommands;
  }

  public List<TimeoutAction> getTimeoutActionObjects() {
    List<TimeoutAction> timeoutActionObjects = new ArrayList<>();

    new TopologicalOrderIterator<>(stateClassObject.getAfterActionGraph()).forEachRemaining(
      timeoutActionObject -> timeoutActionObjects.add((TimeoutAction) timeoutActionObject)
    );

    return timeoutActionObjects;
  }
}
