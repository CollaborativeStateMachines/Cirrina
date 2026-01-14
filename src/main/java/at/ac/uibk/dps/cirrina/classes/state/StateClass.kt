package at.ac.uibk.dps.cirrina.classes.state

import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraphBuilder

/**
 * State class describes the structure of a state.
 *
 * A state contains its properties and action graphs.
 */
class StateClass internal constructor(parameters: Parameters) {
  val name = parameters.name

  val localContextDescription = parameters.transientContextDescription
  val isInitial = parameters.initial
  val isTerminal = parameters.terminal

  val entryActionGraph = ActionGraphBuilder.from(parameters.entryActions).build()
  val exitActionGraph = ActionGraphBuilder.from(parameters.exitActions).build()
  val whileActionGraph = ActionGraphBuilder.from(parameters.whileActions).build()
  val afterActionGraph = ActionGraphBuilder.from(parameters.afterActions).build()

  /**
   * Returns the specific actions across all action graphs.
   *
   * @param type The class type to filter by.
   * @return A list of actions matching the specified type.
   */
  fun <T> getActionsOfType(type: Class<T>): List<T> =
    listOf(entryActionGraph, exitActionGraph, whileActionGraph, afterActionGraph).flatMap {
      it.getActionsOfType(type)
    }

  override fun toString(): String = name

  data class Parameters(
    val name: String,
    val transientContextDescription: Map<String, String>?,
    val initial: Boolean,
    val terminal: Boolean,
    val entryActions: List<Action>,
    val exitActions: List<Action>,
    val whileActions: List<Action>,
    val afterActions: List<Action>,
  )
}
