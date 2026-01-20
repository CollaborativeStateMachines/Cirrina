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
 * @property executionContext the context in which the invocation occurs.
 * @property invokeAction the definition of the service call and associated events.
 */
class ActionInvokeCommand(
  executionContext: ExecutionContext,
  private val invokeAction: InvokeAction,
) : ActionCommand(executionContext) {

  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }

  /**
   * Executes the service invocation logic.
   *
   * @return an empty list of [ActionCommand]s.
   * @throws Exception if the command execution fails due to an internal error.
   */
  override fun execute(): List<ActionCommand> {
    val service = selectServiceImplementation()

    val input = prepareInput(executionContext.scope.extent)

    executionContext.coroutineScope.launch {
      try {
        raiseEvents(service.invoke(input))
      } catch (e: Exception) {
        logger.atWarning().withCause(e).log("service invocation failed")
      }
    }

    return emptyList()
  }

  private fun prepareInput(extent: Extent): List<ContextVariable> =
    invokeAction.input.map { it.evaluate(extent) }

  private fun selectServiceImplementation(): ServiceImplementation {
    val serviceType = invokeAction.serviceType
    val mode = invokeAction.mode

    return executionContext.serviceImplementationSelector.select(serviceType, mode)
      ?: error("no service implementation found for type '$serviceType'")
  }

  private fun raiseEvents(output: List<ContextVariable>) {
    val eventListener = executionContext.eventListener
    val eventHandler = executionContext.eventHandler

    invokeAction.done
      .map { it.withData(output) }
      .forEach { event ->
        if (event.channel == EventChannel.INTERNAL) {
          eventListener.onReceiveEvent(event)
        } else {
          eventHandler.sendEvent(event)
        }
      }
  }
}
