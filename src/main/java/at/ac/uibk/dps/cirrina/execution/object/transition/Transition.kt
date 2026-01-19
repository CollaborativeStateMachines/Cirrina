package at.ac.uibk.dps.cirrina.execution.`object`.transition

import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass
import at.ac.uibk.dps.cirrina.execution.command.ActionCommand
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory
import org.jgrapht.traverse.TopologicalOrderIterator

/**
 * A representation of a state transition within a state machine.
 *
 * This class determines the target state based on whether the transition is a standard or an 'or'
 * (conditional) transition. It also manages the execution of actions associated with the
 * transition.
 *
 * @property transitionClass the definition of the transition including action graphs and targets.
 * @property isOr indicates whether this transition represents an 'or' logic branch.
 */
class Transition(private val transitionClass: TransitionClass, val isOr: Boolean) {

  init {
    require(!isOr || transitionClass.or != null) {
      "or transition must have a valid 'or' target state"
    }
  }

  /** Indicates whether this is an internal transition (no target state change). */
  val isInternal: Boolean
    get() = transitionClass.to == null

  /** The name of the target state this transition leads to, if any. */
  val targetStateName: String?
    get() = if (isOr) transitionClass.or else transitionClass.to

  /**
   * Retrieves the action commands associated with this transition.
   *
   * @param commandFactory the factory used to create the commands.
   * @return a [Result] containing the list of [ActionCommand]s in topological order.
   */
  fun getActionCommands(commandFactory: CommandFactory): Result<List<ActionCommand>> =
    runCatching {
        TopologicalOrderIterator(transitionClass.actionGraph)
          .asSequence()
          .map { commandFactory.createActionCommand(it).getOrThrow() }
          .toList()
      }
      .recoverCatching { e ->
        throw IllegalStateException("could not create transition commands", e)
      }

  override fun toString(): String =
    "Transition(internal=$isInternal, targetStateName=$targetStateName, isOr=$isOr)"
}
