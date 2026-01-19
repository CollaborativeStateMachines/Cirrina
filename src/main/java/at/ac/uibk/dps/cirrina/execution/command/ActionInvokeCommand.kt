package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.action.InvokeAction
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachineEventHandler
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation
import at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*
import at.ac.uibk.dps.cirrina.utils.Time
import com.google.common.flogger.FluentLogger
import kotlinx.coroutines.launch

class ActionInvokeCommand(
  executionContext: ExecutionContext,
  private val invokeAction: InvokeAction,
) : ActionCommand(executionContext) {

  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }

  override fun execute(): Result<List<ActionCommand>> =
    runCatching {
        incrementInvocationCounter()
        val start = Time.timeInMillisecondsSinceStart()

        val serviceImplementation = selectServiceImplementation().getOrThrow()
        val extent = executionContext.scope.extent
        val input = prepareInput(extent).getOrThrow()

        executionContext.coroutineScope.launch {
          serviceImplementation
            .invoke(input, executionContext.scope.id)
            .onSuccess { output ->
              raiseEvents(output, executionContext.eventListener, executionContext.eventHandler)
              measurePerformance(start, serviceImplementation)
            }
            .onFailure { ex ->
              logger
                .atWarning()
                .withCause(ex)
                .log(
                  "service invocation failed for service '%s'",
                  serviceImplementation.informationString,
                )
            }
        }

        emptyList<ActionCommand>()
      }
      .recoverCatching { ex ->
        throw UnsupportedOperationException("could not execute invoke action", ex)
      }

  private fun prepareInput(extent: Extent): Result<List<ContextVariable>> = runCatching {
    invokeAction.input.map { variable -> variable.evaluate(extent).getOrThrow() }
  }

  private fun incrementInvocationCounter() {
    executionContext.counters
      .getCounter(COUNTER_INVOCATIONS)
      .add(1, executionContext.counters.attributesForInvocation())
  }

  private fun selectServiceImplementation(): Result<ServiceImplementation> {
    val serviceType = invokeAction.serviceType
    val mode = invokeAction.mode

    return executionContext.serviceImplementationSelector.select(serviceType, mode).let { optional
      ->
      if (optional.isPresent) Result.success(optional.get())
      else
        Result.failure(
          IllegalArgumentException(
            "could not find a service implementation for the service type '$serviceType'"
          )
        )
    }
  }

  private fun raiseEvents(
    output: List<ContextVariable>,
    eventListener: EventListener,
    eventHandler: StateMachineEventHandler,
  ) {
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

  private fun measurePerformance(start: Double, serviceImplementation: ServiceImplementation) {
    val now = Time.timeInMillisecondsSinceStart()
    val gauges = executionContext.gauges

    val location = if (serviceImplementation.isLocal) "local" else "remote"
    gauges
      .getGauge(GAUGE_ACTION_INVOKE_LATENCY)
      .set(now - start, gauges.attributesForInvocation(location))

    executionContext.raisingEvent?.let { raisingEvent ->
      val delta = Time.timeInMillisecondsSinceEpoch() - raisingEvent.createdTime
      gauges
        .getGauge(GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE)
        .set(delta, gauges.attributesForEvent(raisingEvent.channel.toString()))
    }
  }
}
