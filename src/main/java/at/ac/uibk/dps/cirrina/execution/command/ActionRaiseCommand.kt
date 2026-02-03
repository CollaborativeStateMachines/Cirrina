package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.action.RaiseAction
import io.micrometer.core.instrument.MeterRegistry

class ActionRaiseCommand
internal constructor(
  private val raiseAction: RaiseAction,
  executionContext: ExecutionContext,
  meterRegistry: MeterRegistry,
) : ActionCommand(executionContext, meterRegistry) {

  override fun execute(): List<ActionCommand> {
    val event =
      raiseAction.event.evaluateData(executionContext.scope.extent).run {
        val target = raiseAction.target?.execute(executionContext.scope.extent) as? String
        if (target != null) copy(target = target) else this
      }

    with(executionContext.stateMachineEventHandler) {
      if (event.channel == EventChannel.INTERNAL) propagateToParent(event) else sendEvent(event)
    }

    return emptyList()
  }
}
