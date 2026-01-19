package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.action.RaiseAction
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import java.util.concurrent.atomic.AtomicReference

class ActionRaiseCommand
internal constructor(executionContext: ExecutionContext, private val raiseAction: RaiseAction) :
  ActionCommand(executionContext) {

  private val latency = AtomicReference<Double?>()

  override fun execute(): Result<List<ActionCommand>> = runCatching {
    val extent = executionContext.scope.extent
    val evaluatedEvent = Event.ensureHasEvaluatedData(raiseAction.event, extent)

    dispatch(evaluatedEvent)

    emptyList()
  }

  // Routes the event to either the internal listener or the external handler
  private fun dispatch(event: Event) {
    if (event.channel == EventChannel.INTERNAL) {
      executionContext.eventListener.onReceiveEvent(event)
    } else {
      executionContext.eventHandler.sendEvent(event)
    }
  }
}
