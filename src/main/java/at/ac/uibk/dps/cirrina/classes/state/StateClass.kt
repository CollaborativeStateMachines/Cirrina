package at.ac.uibk.dps.cirrina.classes.state

import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraphBuilder

/**
 * Represents the static definition of a state within the state machine.
 *
 * @property name the unique identifier for the state.
 * @property initial whether this state serves as the entry point of the state machine.
 * @property terminal whether reaching this state concludes the state machine execution.
 * @property staticContextDescription a mapping of static context variables for this state.
 */
class StateClass
internal constructor(
  val name: String,
  val initial: Boolean,
  val terminal: Boolean,
  val staticContextDescription: Map<String, String>?,
  entryActions: List<Action>,
  exitActions: List<Action>,
  whileActions: List<Action>,
  afterActions: List<Action>,
) {
  /** Graph of actions executed immediately upon entering the state. */
  val entryActionGraph = ActionGraphBuilder.from(entryActions).build()

  /** Graph of actions executed just before transitioning out of the state. */
  val exitActionGraph = ActionGraphBuilder.from(exitActions).build()

  /** Graph of actions executed continuously or repeatedly while remaining in the state. */
  val whileActionGraph = ActionGraphBuilder.from(whileActions).build()

  /** Graph of actions executed after a specific event or condition is met within the state. */
  val afterActionGraph = ActionGraphBuilder.from(afterActions).build()

  /**
   * Filters and returns actions of a specific type [T].
   *
   * @return a list of actions matching type [T].
   */
  inline fun <reified T> getActionsOfType(): List<T> =
    listOf(entryActionGraph, exitActionGraph, whileActionGraph, afterActionGraph).flatMap {
      it.getActionsOfType<T>()
    }
}
