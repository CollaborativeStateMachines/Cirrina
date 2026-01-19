package at.ac.uibk.dps.cirrina.execution.`object`.state

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.execution.command.ActionCommand
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory
import at.ac.uibk.dps.cirrina.execution.command.Scope
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import org.jgrapht.Graph
import org.jgrapht.traverse.TopologicalOrderIterator

/**
 * A representation of a state within a [StateMachine] that serves as an execution [Scope].
 *
 * This class manages the static context associated with a state and provides methods to retrieve
 * action commands for different phases of the state's lifecycle (entry, while, exit).
 *
 * @property stateObject the definition of the state containing action graphs and descriptions.
 * @property parent the parent state machine providing the base scope and identifier.
 */
class State(val stateObject: StateClass, private val parent: StateMachine) : Scope {

  private val staticContext: Context

  init {
    // Build the static context from the description or use an empty fallback
    staticContext =
      (stateObject.staticContextDescription?.let { ContextBuilder.from(it) }
          ?: ContextBuilder.empty())
        .inMemoryContext(true)
        .build()
        .getOrElse {
          throw IllegalStateException(
            "could not build static context for state '${stateObject.name}'",
            it,
          )
        }
  }

  /**
   * The current extent of the state, created by extending the parent's extent with the state's
   * static context.
   */
  override val extent: Extent
    get() = parent.extent.extend(staticContext)

  /** The unique identifier for this state, delegated to the parent state machine. */
  override val id: String
    get() = parent.id

  /**
   * Retrieves the entry action commands for this state.
   *
   * @param commandFactory the factory used to create the commands.
   * @return a [Result] containing the list of [ActionCommand]s in topological order.
   */
  fun getEntryActionCommands(commandFactory: CommandFactory): Result<List<ActionCommand>> =
    createCommands(stateObject.entryActionGraph.asTopologicalList(), commandFactory)

  /**
   * Retrieves the while action commands for this state.
   *
   * @param commandFactory the factory used to create the commands.
   * @return a [Result] containing the list of [ActionCommand]s in topological order.
   */
  fun getWhileActionCommands(commandFactory: CommandFactory): Result<List<ActionCommand>> =
    createCommands(stateObject.whileActionGraph.asTopologicalList(), commandFactory)

  /**
   * Retrieves the exit action commands for this state.
   *
   * @param commandFactory the factory used to create the commands.
   * @return a [Result] containing the list of [ActionCommand]s in topological order.
   */
  fun getExitActionCommands(commandFactory: CommandFactory): Result<List<ActionCommand>> =
    createCommands(stateObject.exitActionGraph.asTopologicalList(), commandFactory)

  /**
   * Retrieves the list of timeout actions defined for this state.
   *
   * @return a list of [TimeoutAction]s extracted from the after-action graph.
   */
  fun getTimeoutActionObjects(): List<TimeoutAction> =
    stateObject.afterActionGraph.asTopologicalList().filterIsInstance<TimeoutAction>()

  private fun createCommands(
    actions: List<Action>,
    factory: CommandFactory,
  ): Result<List<ActionCommand>> =
    runCatching { actions.map { factory.createActionCommand(it).getOrThrow() } }
      .recoverCatching { e -> throw IllegalStateException("could not create action commands", e) }

  private fun <V, E> Graph<V, E>.asTopologicalList(): List<V> =
    TopologicalOrderIterator(this).asSequence().toList()
}
