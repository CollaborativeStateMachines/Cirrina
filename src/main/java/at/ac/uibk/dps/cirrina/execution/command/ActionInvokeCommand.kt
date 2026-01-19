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
   * @return a [Result] containing an empty list of [ActionCommand]s on success, or a failure if
   *   service selection or input preparation fails.
   */
  override fun execute(): Result<List<ActionCommand>> =
    selectServiceImplementation()
      .mapCatching { service ->
        val input = prepareInput(executionContext.scope.extent).getOrThrow()

        executionContext.coroutineScope.launch {
          service
            .invoke(input)
            .onSuccess { output -> raiseEvents(output) }
            .onFailure { e -> logger.atWarning().withCause(e).log("service invocation failed") }
        }
        emptyList<ActionCommand>()
      }
      .recoverCatching { e ->
        throw UnsupportedOperationException("could not execute invoke action", e)
      }

  private fun prepareInput(extent: Extent): Result<List<ContextVariable>> = runCatching {
    invokeAction.input.map { it.evaluate(extent).getOrThrow() }
  }

  private fun selectServiceImplementation(): Result<ServiceImplementation> {
    val serviceType = invokeAction.serviceType
    val mode = invokeAction.mode

    return executionContext.serviceImplementationSelector
      .select(serviceType, mode)
      .map { Result.success(it) }
      .orElseGet {
        Result.failure(
          IllegalArgumentException("no service implementation found for type '$serviceType'")
        )
      }
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
