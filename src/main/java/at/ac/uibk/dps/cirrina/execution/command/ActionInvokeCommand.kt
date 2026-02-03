package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.action.InvokeAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ActionInvokeCommand
internal constructor(
  private val invokeAction: InvokeAction,
  executionContext: ExecutionContext,
  meterRegistry: MeterRegistry,
) : ActionCommand(executionContext, meterRegistry) {

  override fun execute(): List<ActionCommand> =
    selectServiceImplementation()
      .let { service -> service to prepareInput(executionContext.scope.extent) }
      .run {
        executionContext.coroutineScope.launch {
          runCatching { first.invoke(second) }
            .onSuccess { raiseEvents(it) }
            .onFailure { logger.error(it) { "service invocation failed" } }
        }
        emptyList()
      }

  private fun selectServiceImplementation(): ServiceImplementation =
    executionContext.serviceImplementationSelector.select(invokeAction.type, invokeAction.mode)
      ?: error("no service implementation found for type '${invokeAction.type}'")

  private fun prepareInput(extent: Extent): List<ContextVariable> =
    invokeAction.input.map { it.evaluate(extent) }

  private fun raiseEvents(output: List<ContextVariable>) =
    invokeAction.raises
      .map { it.copy(data = output) }
      .forEach { event ->
        when (event.channel) {
          EventChannel.INTERNAL -> executionContext.stateMachineEventHandler::propagateToParent
          else -> executionContext.stateMachineEventHandler::sendEvent
        }.invoke(event)
      }
}
