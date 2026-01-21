package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

/**
 * Defines the contract for actions that produce [Event]s as a result of their execution.
 *
 * Implementations of this interface provide a collection of events that the state machine will
 * dispatch to internal listeners or external handlers.
 */
interface EventRaisingAction {
  /**
   * Returns the list of [Event]s to be triggered by this action.
   *
   * @return a list of event descriptions for dispatching.
   */
  fun raises(): List<Event>
}
