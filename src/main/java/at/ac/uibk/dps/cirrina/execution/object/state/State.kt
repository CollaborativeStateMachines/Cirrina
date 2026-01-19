package at.ac.uibk.dps.cirrina.execution.`object`.state

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.execution.command.ActionCommand
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory
import at.ac.uibk.dps.cirrina.execution.command.Scope
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import org.jgrapht.traverse.TopologicalOrderIterator

class State(val stateObject: StateClass, private val parent: StateMachine) : Scope {

  private val staticContext: Context

  init {
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

  override fun getExtent(): Extent = parent.getExtent().extend(staticContext)

  override fun getId(): String = parent.getId()

  fun getEntryActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    stateObject.entryActionGraph.asTopologicalList().map { commandFactory.createActionCommand(it) }

  fun getWhileActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    stateObject.whileActionGraph.asTopologicalList().map { commandFactory.createActionCommand(it) }

  fun getExitActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    stateObject.exitActionGraph.asTopologicalList().map { commandFactory.createActionCommand(it) }

  fun getTimeoutActionObjects(): List<TimeoutAction> =
    stateObject.afterActionGraph.asTopologicalList().filterIsInstance<TimeoutAction>()

  private fun <V, E> org.jgrapht.Graph<V, E>.asTopologicalList(): List<V> =
    TopologicalOrderIterator(this).asSequence().toList()
}
