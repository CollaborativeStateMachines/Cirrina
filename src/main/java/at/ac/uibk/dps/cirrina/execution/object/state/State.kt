package at.ac.uibk.dps.cirrina.execution.`object`.state

import at.ac.uibk.dps.cirrina.execution.command.ActionCommand
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory
import at.ac.uibk.dps.cirrina.execution.command.Scope
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import at.ac.uibk.dps.cirrina.spec.State
import org.jgrapht.Graph
import org.jgrapht.traverse.TopologicalOrderIterator

class State(val state: State, private val parentStateMachine: StateMachine) : Scope {

  private val entryActions: List<Action> = state.entryActions.asTopologicalList()
  private val whileActions: List<Action> = state.whileActions.asTopologicalList()
  private val exitActions: List<Action> = state.exitActions.asTopologicalList()
  private val timeoutActions: List<TimeoutAction> =
    state.afterActions.asTopologicalList().filterIsInstance<TimeoutAction>()

  override val extent: Extent by lazy { parentStateMachine.extent.extend(buildStaticContext()) }

  fun getEntryActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    entryActions.map { commandFactory.createActionCommand(it) }

  fun getWhileActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    whileActions.map { commandFactory.createActionCommand(it) }

  fun getExitActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    exitActions.map { commandFactory.createActionCommand(it) }

  fun getTimeoutActionObjects(): List<TimeoutAction> = timeoutActions

  private fun <V, E> Graph<V, E>.asTopologicalList(): List<V> =
    TopologicalOrderIterator(this).asSequence().toList()

  private fun buildStaticContext() =
    (state.staticContextDescription?.let { Context.from(it) } ?: Context.empty()).getOrThrow()

  override fun toString() = "${this::class.simpleName}(name='${state.name}')"
}
