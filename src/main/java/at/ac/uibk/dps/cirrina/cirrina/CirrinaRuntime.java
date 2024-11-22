package at.ac.uibk.dps.cirrina.cirrina;

import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.context.NatsContext;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.execution.object.event.NatsEventHandler;
import at.ac.uibk.dps.cirrina.runtime.OnlineRuntime;
import at.ac.uibk.dps.cirrina.utils.Id;
import info.schnatterer.mobynamesgenerator.MobyNamesGenerator;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.ResourceAttributes;
import java.io.IOException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;


/**
 * CirrinaRuntime, is the entry-point to the runtime system.
 */
public class CirrinaRuntime extends Cirrina {

  /**
   * Runtime ID.
   */
  private final Id runtimeId = new Id();

  /**
   * Shared arguments.
   */
  private final Args args;

  /**
   * Initializes this main object.
   *
   * @param args The arguments to the main object.
   */
  CirrinaRuntime(Args args) {
    this.args = args;
  }

  /**
   * Run the runtime.
   */
  @Override
  public void run() {
    // Connect to event system
    try (final var eventHandler = newEventHandler()) {
      eventHandler.subscribe(NatsEventHandler.GLOBAL_SOURCE, "*");
      eventHandler.subscribe(NatsEventHandler.PERIPHERAL_SOURCE, "*");

      // Connect to persistent context system and ZooKeeper
      try (final var persistentContext = newPersistentContext()
          ; final var curatorFramework = newCuratorFramework()) {
        // Connect to ZooKeeper
        curatorFramework.start();

        // Acquire OpenTelemetry instance
        final var openTelemetry = getOpenTelemetry();

        // Generate a runtime name
        final var name = MobyNamesGenerator.getRandomName();

        // Create the shared runtime
        final var runtime = new OnlineRuntime(
            name,
            eventHandler,
            persistentContext,
            openTelemetry,
            curatorFramework,
            args.runtimeArgs.deleteJob);

        logger.info("Starting runtime: {}", name);

        // Run, will return when finished
        runtime.run();

        logger.info("Done running");
      }
    } catch (InterruptedException e) {
      logger.info("Interrupted.");

      Thread.currentThread().interrupt();
    } catch (Exception e) {
      logger.error("Could not initialize the shared runtime", e);
    }
  }

  /**
   * Constructs a new event handler according to the provided arguments.
   *
   * @return Event handler.
   * @throws IOException              If the event handler could not be constructed.
   * @throws IllegalArgumentException If the event handler provided is not known.
   */
  protected EventHandler newEventHandler() throws IOException, IllegalArgumentException {
    switch (args.runtimeArgs.eventHandler) {
      case Nats -> {
        return newNatsEventHandler();
      }
    }

    throw new IllegalArgumentException("Unknown event handler '%s'".formatted(args.runtimeArgs.eventHandler));
  }

  /**
   * Constructs a new NATS event handler according to the provided arguments.
   *
   * @return Event handler.
   * @throws IOException If the event handler could not be constructed.
   */
  private NatsEventHandler newNatsEventHandler() throws IOException {
    return new NatsEventHandler(args.runtimeArgs.natsEventHandlerArgs.natsUrl);
  }

  /**
   * Constructs a new persistent context according to the provided arguments.
   *
   * @return Persistent context.
   * @throws IOException              If the event handler could not be constructed.
   * @throws IllegalArgumentException If the persistent context provided is not known.
   */
  protected Context newPersistentContext() throws IOException, IllegalArgumentException {
    switch (args.runtimeArgs.persistentContext) {
      case Nats -> {
        return newNatsPersistentContext();
      }
    }

    throw new IllegalArgumentException("Unknown persistent context '%s'".formatted(args.runtimeArgs.eventHandler));
  }

  /**
   * Constructs a new NATS persistent context according to the provided arguments.
   *
   * @return Persistent context.
   * @throws IOException If the persistent context could not be constructed.
   */
  private NatsContext newNatsPersistentContext() throws IOException {
    return new NatsContext(false, args.runtimeArgs.natsPersistentContextArgs.natsUrl,
        args.runtimeArgs.natsPersistentContextArgs.bucketName);
  }

  /**
   * Constructs a new Curator framework according to the provided arguments.
   *
   * @return Curator framework.
   */
  public CuratorFramework newCuratorFramework() {
    return CuratorFrameworkFactory.builder()
        .connectString(args.runtimeArgs.zooKeeperArgs.connectString)
        .retryPolicy(new ExponentialBackoffRetry(1000, 3))
        .connectionTimeoutMs(args.runtimeArgs.zooKeeperArgs.timeoutInMs)
        .sessionTimeoutMs(args.runtimeArgs.zooKeeperArgs.sessionTimeoutInMs)
        .build();
  }

  /**
   * Returns the OpenTelemetry SDK instance.
   *
   * @return OpenTelemetry SDK.
   */
  protected OpenTelemetry getOpenTelemetry() {
    SpanExporter spanExporter = OtlpGrpcSpanExporter.builder().setEndpoint("http://localhost:4317").build();
    SpanExporter jaegerSpanExporter = JaegerGrpcSpanExporter.builder().setEndpoint("http://localhost:14250").build();

    SpanProcessor otlpSpanProcessor = BatchSpanProcessor.builder(spanExporter).build();
    SpanProcessor jaegerSpanProcessor = BatchSpanProcessor.builder(jaegerSpanExporter).build();

    Resource resource = Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "cirrina"));

    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(otlpSpanProcessor)
        .addSpanProcessor(jaegerSpanProcessor)
        .setResource(resource).build();

    MetricExporter metricExporter = OtlpGrpcMetricExporter.builder().setEndpoint("http://localhost:4317").build();

    PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter).setInterval(java.time.Duration.ofSeconds(5)).build();

    SdkMeterProvider meterProvider = SdkMeterProvider.builder().setResource(resource).registerMetricReader(metricReader).build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)
        .setPropagators(ContextPropagators.create(TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance()))).buildAndRegisterGlobal();

    return GlobalOpenTelemetry.get();
  }
}
