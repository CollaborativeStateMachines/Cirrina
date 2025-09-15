package at.ac.uibk.dps.cirrina.utils

import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableExchange
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import com.sun.net.httpserver.HttpServer
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
import java.net.InetSocketAddress

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

  fun mockHttpServer(handlerBlock: (List<ContextVariable>) -> List<ContextVariable>): HttpServer {
    val httpServer = HttpServer.create(InetSocketAddress(8000), 0)

    httpServer.createContext("/increment") { exchange ->
      val payload = exchange.requestBody.readAllBytes()

      val input =
        ContextVariableProtos.ContextVariables.parseFrom(payload).dataList.map {
          ContextVariableExchange.fromProto(it)
        }

      val output = handlerBlock(input)

      val out =
        ContextVariableProtos.ContextVariables.newBuilder()
          .addAllData(output.map { ContextVariableExchange(it).toProto() })
          .build()
          .toByteArray()

      exchange.sendResponseHeaders(200, out.size.toLong())
      exchange.responseBody.use { stream -> stream.write(out) }
    }

    httpServer.start()

    return httpServer
  }
}
