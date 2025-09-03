package at.ac.uibk.dps.cirrina.runtime;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass;
import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector;
import at.ac.uibk.dps.cirrina.utils.BuildVersion;
import at.ac.uibk.dps.cirrina.utils.Id;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import java.util.List;

/**
 * Offline runtime, a runtime implementation that is meant to not be connected to any services, such as a message queue, key-value store or
 * coordination system. The offline runtime serves to simplify the (programmatic) setup of a runtime system.
 * <p>
 * Being disconnected from these services restricts the functionality of the runtime system. Instead of being able to submit jobs to one or
 * several runtime systems, the offline runtime system requires manual configuration.
 */
public class OfflineRuntime extends Runtime {

  /**
   * The service name.
   */
  private static final String SERVICE_NAME = "cirrina-offline-runtime";

  /**
   * Initializes this offline runtime instance.
   *
   * @param name              Name.
   * @param eventHandler      Event handler.
   * @param persistentContext Persistent context.
   */
  public OfflineRuntime(String name, EventHandler eventHandler, Context persistentContext) {
    super(name, eventHandler, persistentContext, getOpenTelemetry());
  }

  /**
   * Returns a local OpenTelemetry instance that logs to the standard output stream.
   *
   * @return OpenTelemtry.
   */
  private static OpenTelemetry getOpenTelemetry() {
    final var resource = Resource.getDefault()
      .toBuilder()
      .put(ResourceAttributes.SERVICE_NAME, SERVICE_NAME)
      .put(ResourceAttributes.SERVICE_VERSION, BuildVersion.getBuildVersion())
      .build();

    final var sdkTracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
      .setResource(resource)
      .build();

    final var sdkMeterProvider = SdkMeterProvider.builder()
      .registerMetricReader(PeriodicMetricReader.builder(LoggingMetricExporter.create()).build())
      .setResource(resource)
      .build();

    final var sdkLoggerProvider = SdkLoggerProvider.builder()
      .addLogRecordProcessor(
        BatchLogRecordProcessor.builder(SystemOutLogRecordExporter.create()).build()
      )
      .setResource(resource)
      .build();

    return OpenTelemetrySdk.builder()
      .setTracerProvider(sdkTracerProvider)
      .setMeterProvider(sdkMeterProvider)
      .setLoggerProvider(sdkLoggerProvider)
      .setPropagators(
        ContextPropagators.create(
          TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance()
          )
        )
      )
      .build();
  }

  /**
   * Instantiates all state machines of a collaborative state machine.
   * <p>
   * This method exists for convenience, instantiating a full collaborative state machine is not a typical scenario. However, it may be
   * useful for testing.
   *
   * @param collaborativeStateMachineClass Collaborative state machine.
   * @param serviceImplementationSelector  Service implementation selector.
   * @return Instance IDs.
   * @throws UnsupportedOperationException If a state machine could not be instantiated.
   */
  public List<Id> newInstance(
    CollaborativeStateMachineClass collaborativeStateMachineClass,
    ServiceImplementationSelector serviceImplementationSelector
  ) throws UnsupportedOperationException {
    return super.newInstance(collaborativeStateMachineClass, serviceImplementationSelector, -1.0);
  }
}
