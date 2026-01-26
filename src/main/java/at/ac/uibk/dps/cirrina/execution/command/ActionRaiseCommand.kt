package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.action.RaiseAction
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import io.micrometer.core.instrument.MeterRegistry

/**
 * A command responsible for evaluating and dispatching an event defined by a [RaiseAction] within
 * the provided [executionContext].
 *
 * This command ensures that any data associated with the event is evaluated within the current
 * scope before routing the event to the appropriate internal or external handler.
 *
 * @property raiseAction the definition of the event to be raised.
 * @property executionContext the context providing the event listener and state machine handler.
 * @property meterRegistry the registry used for collecting metrics.
 */
class ActionRaiseCommand
internal constructor(
  private val raiseAction: RaiseAction,
  executionContext: ExecutionContext,
  meterRegistry: MeterRegistry,
) : ActionCommand(executionContext, meterRegistry) {

  /**
   * Executes the raise logic by evaluating event data and dispatching it.
   *
   * @return an empty list of [ActionCommand]s.
   * @throws Exception if the command execution fails due to an internal error.
   */
  override fun execute(): List<ActionCommand> =
    Event.ensureHasEvaluatedData(raiseAction.event, executionContext.scope.extent)
      .also { event ->
        when (event.channel) {
          EventChannel.INTERNAL -> executionContext.eventListener::onReceiveEvent
          else -> executionContext.stateMachineEventHandler::sendEvent
        }.invoke(event)
      }
      .run { emptyList() }
}
