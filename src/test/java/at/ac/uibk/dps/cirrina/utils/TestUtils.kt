package at.ac.uibk.dps.cirrina.utils

import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

object TestUtils {
  fun loggingOpenTelemetry(): OpenTelemetry {
    val resource = Resource.getDefault().toBuilder().build()

    return OpenTelemetrySdk.builder()
      .setTracerProvider(
        SdkTracerProvider.builder()
          .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
          .setResource(resource)
          .build()
      )
      .setMeterProvider(
        SdkMeterProvider.builder()
          .registerMetricReader(
            PeriodicMetricReader.builder(LoggingMetricExporter.create()).build()
          )
          .setResource(resource)
          .build()
      )
      .setLoggerProvider(
        SdkLoggerProvider.builder()
          .addLogRecordProcessor(
            BatchLogRecordProcessor.builder(SystemOutLogRecordExporter.create()).build()
          )
          .setResource(resource)
          .build()
      )
      .setPropagators(
        ContextPropagators.create(
          TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance(),
          )
        )
      )
      .build()
  }

  fun mockPersistentContext(
    createBlock: InMemoryContext.() -> Unit = {},
    assignBlock: (superAssign: (String, Any) -> Int, name: String, value: Any) -> Int =
      { superAssign, name, value ->
        superAssign(name, value)
      },
  ): InMemoryContext {
    val mockPersistentContext =
      object : InMemoryContext(true) {
        override fun assign(name: String, value: Any): Int {
          return assignBlock({ n, v -> super.assign(n, v) }, name, value)
        }
      }
    mockPersistentContext.createBlock()
    return mockPersistentContext
  }
}
