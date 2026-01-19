package at.ac.uibk.dps.cirrina.execution.`object`.transition

import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass
import at.ac.uibk.dps.cirrina.execution.command.ActionCommand
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory
import org.jgrapht.traverse.TopologicalOrderIterator

class Transition(private val transitionClass: TransitionClass, val isOr: Boolean) {

  init {
    require(!isOr || transitionClass.or != null) {
      "or transition must have a valid 'or' target state"
    }
  }

  val isInternal: Boolean
    get() = transitionClass.to == null

  val targetStateName: String?
    get() = if (isOr) transitionClass.or else transitionClass.to

  fun getActionCommands(commandFactory: CommandFactory): List<ActionCommand> =
    TopologicalOrderIterator(transitionClass.actionGraph)
      .asSequence()
      .map { commandFactory.createActionCommand(it) }
      .toList()

  override fun toString(): String =
    "Transition(internal=$isInternal, targetStateName=$targetStateName, isElse=$isOr)"
}
