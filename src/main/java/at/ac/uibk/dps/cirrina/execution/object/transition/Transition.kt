package at.ac.uibk.dps.cirrina.execution.`object`.transition

import at.ac.uibk.dps.cirrina.execution.command.ActionCommand
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.spec.Transition
import org.jgrapht.traverse.TopologicalOrderIterator

class Transition(private val transition: Transition, val isOr: Boolean) {

  private val sortedActions: List<Action> =
    TopologicalOrderIterator(transition.actions).asSequence().toList()

  init {
    require(!isOr || transition.or != null) { "or transition must have a valid 'or' target state" }
  }

  val isInternal: Boolean
    get() = transition.to == null

  val targetStateName: String?
    get() = if (isOr) transition.or else transition.to

  fun getActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    sortedActions.map { commandFactory.createActionCommand(it) }

  override fun toString(): String =
    "${this::class.simpleName}(internal=$isInternal, target=$targetStateName, or=$isOr)"
}
