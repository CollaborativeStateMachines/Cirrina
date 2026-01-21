package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

/**
 * An action that schedules a [delay] before executing a specific [do].
 *
 * @property name the unique identifier for this timeout action.
 * @property delay the expression determining the wait time in milliseconds.
 * @property do the action to be triggered once the delay expires.
 */
class TimeoutAction
internal constructor(val name: String, val delay: Expression, val `do`: Action) :
  Action(), EventRaisingAction {

  /**
   * Returns the list of [Event]s to be triggered by this action.
   *
   * @return the event raised by this action.
   */
  override fun raises(): List<Event> =
    (`do` as? RaiseAction)?.let { listOf(it.event) } ?: emptyList()
}
