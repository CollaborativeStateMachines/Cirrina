package at.ac.uibk.dps.cirrina.classes.state;

import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionGraph;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionGraphBuilder;
import at.ac.uibk.dps.cirrina.io.plantuml.Exportable;
import at.ac.uibk.dps.cirrina.io.plantuml.PlantUmlVisitor;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * State class, describes the structure of a state.
 * <p>
 * A state contains its properties and action graphs.
 */
public final class StateClass implements Exportable {

  private final UUID parentStateMachineClassId;

  private final String name;

  private final @Nullable Map<String, String> localContextDescription;

  private final boolean initial;
  private final boolean terminal;

  private final ActionGraph entryActionGraph;
  private final ActionGraph exitActionGraph;
  private final ActionGraph whileActionGraph;
  private final ActionGraph afterActionGraph;

  /**
   * Initializes this state class instance.
   *
   * @param baseParameters parameters
   */
  StateClass(BaseParameters baseParameters) {
    this.parentStateMachineClassId = baseParameters.parentStateMachineId;

    this.name = baseParameters.name;

    this.localContextDescription = baseParameters.localContextDescription;

    this.initial = baseParameters.initial;
    this.terminal = baseParameters.terminal;

    this.entryActionGraph = ActionGraphBuilder.from(baseParameters.entryActions).build();
    this.exitActionGraph = ActionGraphBuilder.from(baseParameters.exitActions).build();
    this.whileActionGraph = ActionGraphBuilder.from(baseParameters.whileActions).build();
    this.afterActionGraph = ActionGraphBuilder.from(baseParameters.afterActions).build();
  }

  /**
   * Initializes this state class instance.
   *
   * @param childParameters parameters
   */
  StateClass(ChildParameters childParameters) {
    this.parentStateMachineClassId = childParameters.parentStateMachineId;

    final var baseState = childParameters.baseStateClass;

    this.name = baseState.name;

    this.localContextDescription = baseState.localContextDescription;

    this.initial = childParameters.initial || baseState.initial;
    this.terminal = childParameters.terminal || baseState.terminal;

    this.entryActionGraph = ActionGraphBuilder.extend(
      new ActionGraph(baseState.entryActionGraph),
      childParameters.entryActions
    ).build();
    this.exitActionGraph = ActionGraphBuilder.extend(
      new ActionGraph(baseState.exitActionGraph),
      childParameters.exitActions
    ).build();
    this.whileActionGraph = ActionGraphBuilder.extend(
      new ActionGraph(baseState.whileActionGraph),
      childParameters.whileActions
    ).build();
    this.afterActionGraph = ActionGraphBuilder.extend(
      new ActionGraph(baseState.afterActionGraph),
      childParameters.afterActions
    ).build();
  }

  /**
   * Return a string representation.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * PlantUML visitor accept.
   *
   * @param visitor PlantUML visitor
   */
  @Override
  public void accept(PlantUmlVisitor visitor) {
    visitor.visit(this);
  }

  /**
   * Returns the is initial flag.
   *
   * @return is initial
   */
  public boolean isInitial() {
    return initial;
  }

  /**
   * Returns the is terminal flag.
   *
   * @return is terminal
   */
  public boolean isTerminal() {
    return terminal;
  }

  /**
   * Returns the parent state machine ID.
   *
   * @return parent state machine ID
   */
  public UUID getParentStateMachineClassId() {
    return parentStateMachineClassId;
  }

  /**
   * Returns the state name.
   *
   * @return state name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the local context description.
   *
   * @return local context description
   */
  public Optional<Map<String, String>> getLocalContextDescription() {
    return Optional.ofNullable(localContextDescription);
  }

  /**
   * Returns the entry action graph.
   *
   * @return entry action graph
   */
  public ActionGraph getEntryActionGraph() {
    return entryActionGraph;
  }

  /**
   * Returns the exit action graph.
   *
   * @return exit action graph
   */
  public ActionGraph getExitActionGraph() {
    return exitActionGraph;
  }

  /**
   * Returns the while action graph.
   *
   * @return while action graph
   */
  public ActionGraph getWhileActionGraph() {
    return whileActionGraph;
  }

  /**
   * Returns the after action graph.
   *
   * @return after action graph
   */
  public ActionGraph getAfterActionGraph() {
    return afterActionGraph;
  }

  /**
   * Returns the actions of a specific type.
   *
   * @param type action type class
   * @param <T>  action type
   * @return actions of type
   */
  public <T> List<T> getActionsOfType(Class<T> type) {
    return Stream.of(entryActionGraph, exitActionGraph, whileActionGraph, afterActionGraph)
      .map(actionGraph -> actionGraph.getActionsOfType(type))
      .flatMap(Collection::stream)
      .toList();
  }

  /**
   * Base state parameters.
   *
   * @param parentStateMachineId ID of the parent state machine class
   * @param name                    name of the state
   * @param localContextDescription local context description
   * @param initial                 is initial
   * @param terminal                is terminal
   * @param entryActions            entry actions
   * @param exitActions             exit actions
   * @param whileActions            while actions
   * @param afterActions            after actions
   */
  record BaseParameters(
    UUID parentStateMachineId,
    String name,
    @Nullable Map<String, String> localContextDescription,
    boolean initial,
    boolean terminal,
    List<Action> entryActions,
    List<Action> exitActions,
    List<Action> whileActions,
    List<Action> afterActions
  ) {}

  /**
   * Child state parameters.
   *
   * @param parentStateMachineId ID of the parent state machine class
   * @param initial              is initial
   * @param terminal             is terminal
   * @param entryActions         entry actions
   * @param exitActions          exit actions
   * @param whileActions         while actions
   * @param afterActions         after actions
   * @param baseStateClass       base state class
   */
  record ChildParameters(
    UUID parentStateMachineId,
    boolean initial,
    boolean terminal,
    List<Action> entryActions,
    List<Action> exitActions,
    List<Action> whileActions,
    List<Action> afterActions,
    StateClass baseStateClass
  ) {}
}
