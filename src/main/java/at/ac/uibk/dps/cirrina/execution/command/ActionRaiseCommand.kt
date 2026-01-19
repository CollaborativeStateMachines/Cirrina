package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.action.RaiseAction
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

/**
 * A command responsible for evaluating and dispatching an event defined by a [RaiseAction] within
 * the provided [executionContext].
 *
 * This command ensures that any data associated with the event is evaluated within the current
 * scope before routing the event to the appropriate internal or external handler.
 *
 * @property executionContext the context providing the event listener and state machine handler.
 * @property raiseAction the definition of the event to be raised.
 */
class ActionRaiseCommand
internal constructor(executionContext: ExecutionContext, private val raiseAction: RaiseAction) :
  ActionCommand(executionContext) {

  /**
   * Executes the raise logic by evaluating event data and dispatching it.
   *
   * @return a [Result] containing an empty list of [ActionCommand]s on success, as raise actions do
   *   not generate further commands, or a failure if event data evaluation fails.
   */
  override fun execute(): Result<List<ActionCommand>> =
    runCatching { Event.ensureHasEvaluatedData(raiseAction.event, executionContext.scope.extent) }
      .onSuccess { evaluatedEvent -> dispatch(evaluatedEvent) }
      .map { emptyList<ActionCommand>() }
      .recoverCatching { e -> throw IllegalStateException("could not execute raise action", e) }

  private fun dispatch(event: Event) {
    if (event.channel == EventChannel.INTERNAL) {
      executionContext.eventListener.onReceiveEvent(event)
    } else {
      executionContext.eventHandler.sendEvent(event)
    }
  }
}
