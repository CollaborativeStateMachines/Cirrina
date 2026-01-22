package at.ac.uibk.dps.cirrina.execution.`object`.statemachine

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler

/**
 * A handler responsible for routing events from a [stateMachine] to an underlying [eventHandler].
 *
 * This class acts as a bridge, ensuring that events sent by the state machine are correctly
 * associated with the machine's unique identifier.
 *
 * @property stateMachine the state machine instance associated with this handler.
 * @property eventHandler the underlying handler used for dispatching events.
 */
class StateMachineEventHandler(
  private val stateMachine: StateMachine,
  private val eventHandler: EventHandler,
) {

  /**
   * Sends an [event] to the underlying event handler, tagged with the state machine's identifier.
   *
   * @param event the event to be sent.
   */
  fun sendEvent(event: Event) {
    eventHandler.sendEvent(event, stateMachine.id)
  }
}
