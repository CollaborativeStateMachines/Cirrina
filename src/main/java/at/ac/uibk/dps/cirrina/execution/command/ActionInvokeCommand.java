package at.ac.uibk.dps.cirrina.execution.command;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.COUNTER_INVOCATIONS;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ACTION_INVOKE_LATENCY;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE;

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel;
import at.ac.uibk.dps.cirrina.execution.object.action.InvokeAction;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.event.EventListener;
import at.ac.uibk.dps.cirrina.execution.object.statemachine.StateMachineEventHandler;
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation;
import at.ac.uibk.dps.cirrina.utils.Time;
import com.google.common.flogger.FluentLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Action invoke command, performs a service type invocation.
 * <p>
 * The execution of this command will use the service implementation selector to select the best matching service implementation and perform
 * the invocation.
 * <p>
 * An invocation is always asynchronous, and subsequent done events are generated and handled by the containing state machine instance.
 */
public final class ActionInvokeCommand extends ActionCommand {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  public List<ActionCommand> execute() throws UnsupportedOperationException {
    incrementInvocationCounter();
    final var start = Time.timeInMillisecondsSinceStart();

    try {
      final var serviceImplementation = selectServiceImplementation();

      final var commands = new ArrayList<ActionCommand>();

      final var extent = executionContext.scope().getExtent();
      final var eventListener = executionContext.eventListener();
      final var eventHandler = executionContext.eventHandler();

      List<ContextVariable> input = prepareInput(extent);

      // Invoke (asynchronously)
      serviceImplementation
        .invoke(input, executionContext.scope().getId())
        .whenComplete((output, e) -> {
          if (e != null) {
            logger
              .atWarning()
              .log(
                "Service invocation failed for service '%s'",
                serviceImplementation.getInformationString()
              );
          } else {
            raiseEvents(output, eventListener, eventHandler);
            measurePerformance(start, serviceImplementation);
          }
        });

      return commands;
    } catch (Exception e) {
      throw new UnsupportedOperationException("Could not execute invoke action", e);
    }
  }

  /**
   * Evaluate all input variables.
   *
   * @return List of evaluated input variables.
   */
  private List<ContextVariable> prepareInput(Extent extent) {
    return invokeAction
      .getInput()
      .stream()
      .map(variable -> variable.evaluate(extent))
      .collect(Collectors.toList());
  }

  /**
   * Increment the invocation counter.
   */
  private void incrementInvocationCounter() {
    executionContext
      .counters()
      .getCounter(COUNTER_INVOCATIONS)
      .add(1, executionContext.counters().attributesForInvocation());
  }

  /**
   * Selects the service implementation for the service type of this action.
   *
   * @return The service implementation.
   */
  private ServiceImplementation selectServiceImplementation() {
    final var serviceType = invokeAction.getServiceType();
    final var isLocal = invokeAction.isLocal();
    final var serviceImplementationSelector = executionContext.serviceImplementationSelector();

    return serviceImplementationSelector
      .select(serviceType, isLocal)
      .orElseThrow(() ->
        new IllegalArgumentException(
          "Could not find a service implementation for the service type '%s'".formatted(serviceType)
        )
      );
  }

  /**
   * Raise all events with output data as event data.
   *
   * @param output        Output data.
   * @param eventListener Event listener.
   * @param eventHandler  Event handler.
   */
  private void raiseEvents(
    List<ContextVariable> output,
    EventListener eventListener,
    StateMachineEventHandler eventHandler
  ) {
    invokeAction
      .getDone()
      .stream()
      .map(event -> event.withData(output))
      .forEach(event -> {
        if (event.getChannel() == EventChannel.INTERNAL) {
          eventListener.onReceiveEvent(event);
        } else {
          eventHandler.sendEvent(event);
        }
      });
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

    gauges
      .getGauge(GAUGE_ACTION_INVOKE_LATENCY)
      .set(
        now - start,
        gauges.attributesForInvocation(serviceImplementation.isLocal() ? "local" : "remote")
      );

    // Measure inclusive response time
    final var raisingEvent = executionContext.raisingEvent();
    if (raisingEvent != null) {
      gauges
        .getGauge(GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE)
        .set(
          Time.timeInMillisecondsSinceEpoch() - raisingEvent.getCreatedTime(),
          gauges.attributesForEvent(raisingEvent.getChannel().toString())
        );
    }
  }
}
