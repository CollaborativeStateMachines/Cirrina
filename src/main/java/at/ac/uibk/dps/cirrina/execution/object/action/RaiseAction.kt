package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

/**
 * An action that explicitly produces a single [event] for dispatching.
 *
 * @property event the event to be raised.
 */
class RaiseAction(val event: Event) : Action(), EventRaisingAction {

  /**
   * Returns the list of [Event]s to be triggered by this action.
   *
   * @return the event raised by this action.
   */
  override fun raises(): List<Event> = listOf(event)
}
