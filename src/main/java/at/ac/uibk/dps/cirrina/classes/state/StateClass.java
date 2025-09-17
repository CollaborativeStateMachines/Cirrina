package at.ac.uibk.dps.cirrina.classes.state;

import at.ac.uibk.dps.cirrina.csm.Csml.ContextDescription;
import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionGraph;
import at.ac.uibk.dps.cirrina.execution.object.action.ActionGraphBuilder;
import at.ac.uibk.dps.cirrina.io.plantuml.Exportable;
import at.ac.uibk.dps.cirrina.io.plantuml.PlantUmlVisitor;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * State class, describes the structure of a state.
 * <p>
 * A state contains its properties and action graphs.
 */
public final class StateClass implements Exportable {

  /**
   * ID of the parent state machine class.
   */
  private final UUID parentStateMachineClassId;

  /**
   * State name.
   */
  private final String name;

  /**
   * Local context description, can be null in case the state has no declared local context.
   */
  private final @Nullable ContextDescription localContextDescription;

  /**
   * Flag that indicates whether this state is initial.
   */
  private final boolean initial;

  /**
   * Flag that indicates whether this state is terminal.
   */
  private final boolean terminal;

  /**
   * The entry action graph.
   */
  private final ActionGraph entryActionGraph;

  /**
   * The exit action graph.
   */
  private final ActionGraph exitActionGraph;

  /**
   * The while action graph.
   */
  private final ActionGraph whileActionGraph;

  /**
   * The after action graph.
   */
  private final ActionGraph afterActionGraph;

  /**
   * Initializes this state class instance.
   *
   * @param baseParameters Parameters.
   */
  StateClass(BaseParameters baseParameters) {
    this.parentStateMachineClassId = baseParameters.parentStateMachineId;

    this.name = baseParameters.name;

    this.localContextDescription = baseParameters.localContextClass;

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
   * @param childParameters Parameters.
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
   * @return String representation.
   */
  @Override
  public String toString() {
    return name;
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
   * Returns the is initial flag.
   *
   * @return Is initial.
   */
  public boolean isInitial() {
    return initial;
  }

  /**
   * Returns the is terminal flag.
   *
   * @return Is terminal.
   */
  public boolean isTerminal() {
    return terminal;
  }

  /**
   * Returns the parent state machine ID.
   *
   * @return Parent state machine ID.
   */
  public UUID getParentStateMachineClassId() {
    return parentStateMachineClassId;
  }

  /**
   * Returns the state name.
   *
   * @return State name.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the local context description.
   *
   * @return Local context description.
   */
  public Optional<ContextDescription> getLocalContextDescription() {
    return Optional.ofNullable(localContextDescription);
  }

  /**
   * Returns the entry action graph.
   *
   * @return Entry action graph.
   */
  public ActionGraph getEntryActionGraph() {
    return entryActionGraph;
  }

  /**
   * Returns the exit action graph.
   *
   * @return Exit action graph.
   */
  public ActionGraph getExitActionGraph() {
    return exitActionGraph;
  }

  /**
   * Returns the while action graph.
   *
   * @return While action graph.
   */
  public ActionGraph getWhileActionGraph() {
    return whileActionGraph;
  }

  /**
   * Returns the after action graph.
   *
   * @return After action graph.
   */
  public ActionGraph getAfterActionGraph() {
    return afterActionGraph;
  }

  /**
   * Returns the actions of a specific type.
   *
   * @param type Action type class.
   * @param <T>  Action type.
   * @return Actions of type.
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
   * @param parentStateMachineId ID of the parent state machine class.
   * @param name                 Name of the state.
   * @param localContextClass    Local context class.
   * @param initial              Is initial.
   * @param terminal             Is terminal.
   * @param entryActions         Entry actions.
   * @param exitActions          Exit actions.
   * @param whileActions         While actions.
   * @param afterActions         After actions.
   */
  record BaseParameters(
    UUID parentStateMachineId,
    String name,
    @Nullable ContextDescription localContextClass,
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
   * @param parentStateMachineId ID of the parent state machine class.
   * @param initial              Is initial.
   * @param terminal             Is terminal.
   * @param entryActions         Entry actions.
   * @param exitActions          Exit actions.
   * @param whileActions         While actions.
   * @param afterActions         After actions.
   * @param baseStateClass       Base state class.
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
