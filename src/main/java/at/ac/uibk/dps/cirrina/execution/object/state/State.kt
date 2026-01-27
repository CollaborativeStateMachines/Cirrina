package at.ac.uibk.dps.cirrina.execution.`object`.state

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.execution.command.ActionCommand
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory
import at.ac.uibk.dps.cirrina.execution.command.Scope
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import org.apache.commons.lang3.builder.ToStringBuilder
import org.jgrapht.Graph
import org.jgrapht.traverse.TopologicalOrderIterator

/**
 * A representation of a state within a [StateMachine] that serves as an execution [Scope].
 *
 * This class manages the static context associated with a state and provides methods to retrieve
 * action commands for different phases of the state's lifecycle (entry, while, exit).
 *
 * @property stateClass the definition of the state containing action graphs and descriptions.
 * @property parentStateMachine the parent state machine providing the base scope and identifier.
 */
class State(val stateClass: StateClass, private val parentStateMachine: StateMachine) : Scope {

  private val entryActions: List<Action> = stateClass.entryActionGraph.asTopologicalList()
  private val whileActions: List<Action> = stateClass.whileActionGraph.asTopologicalList()
  private val exitActions: List<Action> = stateClass.exitActionGraph.asTopologicalList()
  private val timeoutActions: List<TimeoutAction> =
    stateClass.afterActionGraph.asTopologicalList().filterIsInstance<TimeoutAction>()

  /**
   * The current extent of the state, created by extending the parent's extent with the state's
   * static context.
   */
  override val extent: Extent by lazy { parentStateMachine.extent.extend(buildStaticContext()) }

  /**
   * Retrieves the entry action commands for this state.
   *
   * @param commandFactory the factory used to create the commands.
   * @return the list of [ActionCommand]s.
   */
  fun getEntryActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    entryActions.map { commandFactory.createActionCommand(it) }

  /**
   * Retrieves the while action commands for this state.
   *
   * @param commandFactory the factory used to create the commands.
   * @return the list of [ActionCommand]s.
   */
  fun getWhileActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    whileActions.map { commandFactory.createActionCommand(it) }

  /**
   * Retrieves the exit action commands for this state.
   *
   * @param commandFactory the factory used to create the commands.
   * @return the list of [ActionCommand]s.
   */
  fun getExitActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    exitActions.map { commandFactory.createActionCommand(it) }

  /**
   * Retrieves the list of timeout actions defined for this state.
   *
   * @return a list of [TimeoutAction]s.
   */
  fun getTimeoutActionObjects(): List<TimeoutAction> = timeoutActions

  private fun <V, E> Graph<V, E>.asTopologicalList(): List<V> =
    TopologicalOrderIterator(this).asSequence().toList()

  private fun buildStaticContext() =
    (stateClass.staticContextDescription?.let { ContextBuilder.from(it) } ?: ContextBuilder.empty())
      .inMemoryContext()
      .build()
      .getOrThrow()

  override fun toString() = ToStringBuilder(this).append("name", stateClass.name).toString()
}
