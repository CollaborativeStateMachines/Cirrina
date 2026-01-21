package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.action.InvokeAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation
import com.google.common.flogger.FluentLogger
import kotlinx.coroutines.launch

/**
 * A command responsible for invoking an external service as defined by an [InvokeAction] within the
 * provided [executionContext].
 *
 * This command selects an appropriate service implementation, prepares the required input
 * variables, and triggers the service invocation asynchronously.
 *
 * @property invokeAction the definition of the service call and associated events.
 * @property executionContext the context in which the invocation occurs.
 */
class ActionInvokeCommand(
  private val invokeAction: InvokeAction,
  executionContext: ExecutionContext,
) : ActionCommand(executionContext) {

  /**
   * Executes the service invocation logic.
   *
   * @return an empty list of [ActionCommand]s.
   * @throws Exception if the command execution fails due to an internal error.
   */
  override fun execute(): List<ActionCommand> =
    selectServiceImplementation()
      .let { service -> service to prepareInput(executionContext.scope.extent) }
      .run {
        executionContext.coroutineScope.launch {
          runCatching { first.invoke(second) }
            .onSuccess { raiseEvents(it) }
            .onFailure { logger.atWarning().withCause(it).log("service invocation failed") }
        }
        emptyList()
      }

  private fun selectServiceImplementation(): ServiceImplementation =
    executionContext.serviceImplementationSelector.select(
      invokeAction.serviceType,
      invokeAction.mode,
    ) ?: error("no service implementation found for type '$invokeAction.serviceType'")

  private fun prepareInput(extent: Extent): List<ContextVariable> =
    invokeAction.input.map { it.evaluate(extent) }

  private fun raiseEvents(output: List<ContextVariable>) =
    invokeAction.done
      .map { it.withData(output) }
      .forEach { event ->
        when (event.channel) {
          EventChannel.INTERNAL -> executionContext.eventListener::onReceiveEvent
          else -> executionContext.eventHandler::sendEvent
        }.invoke(event)
      }

  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }
}
