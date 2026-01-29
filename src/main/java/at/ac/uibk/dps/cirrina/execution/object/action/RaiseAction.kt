package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

/**
 * An action that explicitly produces a single [event] for dispatching.
 *
 * @property event the event to be raised.
 * @property target the expression determining the target of the event.
 */
class RaiseAction internal constructor(val event: Event, val target: Expression?) :
  Action(), EventRaisingAction {

  /**
   * Returns the list of [Event]s to be triggered by this action.
   *
   * @return the event raised by this action.
   */
  override fun raises(): List<Event> = listOf(event)
}
