package at.ac.uibk.dps.cirrina.execution.command;

import static at.ac.uibk.dps.cirrina.cirrina.Cirrina.logging;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*;
import static at.ac.uibk.dps.cirrina.cirrina.Cirrina.tracer;
import static at.ac.uibk.dps.cirrina.cirrina.Cirrina.tracing;

import at.ac.uibk.dps.cirrina.execution.object.action.InvokeAction;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.event.EventListener;
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import at.ac.uibk.dps.cirrina.utils.Time;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Action invoke command, performs a service type invocation.
 * <p>
 * The execution of this command will use the service implementation selector to select the best matching service implementation and perform
 * the invocation.
 * <p>
 * An invocation is always asynchronous, and subsequent done events are generated and handled by the containing state machine instance.
 */
public final class ActionInvokeCommand extends ActionCommand {

  private static final Logger logger = LogManager.getLogger();

  private final InvokeAction invokeAction;

  /**
   * Initializes this action invoke commands.
   *
   * @param executionContext Execution context.
   * @param invokeAction     Invoke action.
   */
  ActionInvokeCommand(ExecutionContext executionContext, InvokeAction invokeAction) {
    super(executionContext);

    this.invokeAction = invokeAction;
  }

  @Override
  public List<ActionCommand> execute(TracingAttributes tracingAttributes, Span parentSpan) throws UnsupportedOperationException {
    logging.logAction(invokeAction.toString(), tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
    Span span = tracing.initializeSpan("Invoke Action", tracer, parentSpan,
        Map.of( ATTR_STATE_MACHINE_ID, tracingAttributes.getStateMachineId(),
            ATTR_STATE_MACHINE_NAME, tracingAttributes.getStateMachineName(),
            ATTR_PARENT_STATE_MACHINE_ID, tracingAttributes.getParentStateMachineId(),
            ATTR_PARENT_STATE_MACHINE_NAME, tracingAttributes.getParentStateMachineName()));

    incrementInvocationCounter();
    final var start = Time.timeInMillisecondsSinceStart();

    try(Scope scope = span.makeCurrent()) {
      final var serviceImplementation = selectServiceImplementation(tracingAttributes, span);

      final var commands = new ArrayList<ActionCommand>();

      final var extent = executionContext.scope().getExtent();
      final var eventListener = executionContext.eventListener();

      List<ContextVariable> input = prepareInput(extent);

      // Invoke (asynchronously)
      serviceImplementation.invoke(input, executionContext.scope().getId(), tracingAttributes, span)
          .exceptionally(e -> {
            logger.error("Service invocation failed for service '{}': {}",
                serviceImplementation.getInformationString(), e.getMessage(), e);
            return null;
          }).thenAccept(output -> {
            assignServiceOutput(output, extent);
            raiseEvents(output, eventListener, span);
            measurePerformance(start, serviceImplementation);
          });

      return commands;
    } catch (Exception e) {
      logging.logExeption(tracingAttributes.getStateMachineId(), e, tracingAttributes.getStateMachineName());
      tracing.recordException(e, span);
      throw new UnsupportedOperationException("Could not execute invoke action", e);
    } finally {
      span.end();
    }
  }

  /**
   * Evaluate all input variables.
   *
   * @return List of evaluated input variables.
   */
  private List<ContextVariable> prepareInput(Extent extent) {
    return invokeAction.getInput().stream()
        .map(variable -> variable.evaluate(extent))
        .collect(Collectors.toList());
  }

  /**
   * Increment the invocation counter.
   */
  private void incrementInvocationCounter() {
    executionContext.counters().getCounter(COUNTER_INVOCATIONS)
        .add(1, executionContext.counters().attributesForInvocation());
  }

  /**
   * Selects the service implementation for the service type of this action.
   *
   * @return The service implementation.
   */
  private ServiceImplementation selectServiceImplementation(TracingAttributes tracingAttributes, Span parentSpan) {
    Span span = tracing.initializeSpan("Selecting Service Implementation", tracer, parentSpan,
        Map.of( ATTR_STATE_MACHINE_ID, tracingAttributes.getStateMachineId(),
            ATTR_STATE_MACHINE_NAME, tracingAttributes.getStateMachineName(),
            ATTR_PARENT_STATE_MACHINE_ID, tracingAttributes.getParentStateMachineId(),
            ATTR_PARENT_STATE_MACHINE_NAME, tracingAttributes.getParentStateMachineName()));

    final var serviceType = invokeAction.getServiceType();
    final var isLocal = invokeAction.isLocal();
    final var serviceImplementationSelector = executionContext.serviceImplementationSelector();

    try(Scope scope = span.makeCurrent()) {
      return serviceImplementationSelector.select(serviceType, isLocal, tracingAttributes, span)
          .orElseThrow(
              () -> new IllegalArgumentException(
                  "Could not find a service implementation for the service type '%s'".formatted(serviceType)));
    } catch (IllegalArgumentException e) {
      tracing.recordException(e, span);
      throw e;
    } finally {
      span.end();
    }
  }

  /**
   * Assign service output to the provided output context variables
   *
   * @param output Service output.
   * @param extent Extent.
   */
  private void assignServiceOutput(List<ContextVariable> output, Extent extent) {
    for (final var outputReference : invokeAction.getOutput()) {
      output.stream()
          .filter(variable -> variable.name().equals(outputReference.getReference()))
          .findFirst()
          .ifPresentOrElse(
              outputVariable -> {
                try {
                  extent.trySet(outputReference.getReference(), outputVariable.value());
                } catch (Exception e) {
                  logger.error(
                      "Failed to assign service output to variable '{}': {}",
                      outputReference.getReference(), e.getMessage(), e
                  );
                }
              },
              () -> logger.warn(
                  "Service output does not contain expected variable '{}'",
                  outputReference.getReference()
              )
          );
    }
  }

  /**
   * Raise all events (internally) with output data as event data.
   *
   * @param output        Output data.
   * @param eventListener Event listener.
   */
  private void raiseEvents(List<ContextVariable> output, EventListener eventListener, Span span) {
    invokeAction.getDone().stream()
        .map(event -> event.withData(output))
        .forEach(event -> eventListener.onReceiveEvent(event, span));
  }

  /**
   * Measure the performance of the service invocation.
   *
   * @param start                 Start time.
   * @param serviceImplementation Service implementation.
   */
  private void measurePerformance(double start, ServiceImplementation serviceImplementation) {
    // Measure latency
    final var now = Time.timeInMillisecondsSinceStart();
    final var gauges = executionContext.gauges();

    gauges.getGauge(GAUGE_ACTION_INVOKE_LATENCY).set(now - start,
        gauges.attributesForInvocation(
            serviceImplementation.isLocal() ? "local" : "remote"
        ));

    // Measure inclusive response time
    final var raisingEvent = executionContext.raisingEvent();
    if (raisingEvent != null) {
      gauges.getGauge(GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE).set(
          Time.timeInMillisecondsSinceEpoch() - raisingEvent.getCreatedTime(),
          gauges.attributesForEvent(raisingEvent.getChannel().toString())
      );
    }
  }
}
