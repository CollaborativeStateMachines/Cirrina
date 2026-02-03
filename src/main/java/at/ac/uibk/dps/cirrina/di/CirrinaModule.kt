package at.ac.uibk.dps.cirrina.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.Cirrina
import at.ac.uibk.dps.cirrina.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.cirrina.EventProvider
import at.ac.uibk.dps.cirrina.cirrina.PersistentContextProvider
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.EtcdContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.GLOBAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.PERIPHERAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.`object`.event.ZenohEventHandler
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.utils.getBuildVersion
import dagger.Module
import dagger.Provides
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.influx.InfluxConfig
import io.micrometer.influx.InfluxMeterRegistry
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.handler.DefaultTracingObservationHandler
import io.micrometer.tracing.otel.bridge.OtelBaggageManager
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ServiceAttributes
import jakarta.inject.Named
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.net.URI
import java.time.Duration
import java.util.UUID
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class CsmMain

@Module
class CirrinaModule {

  @Provides
  @Singleton
  @Named("identifier")
  fun provideIdentifier(): String = "cirrina.${UUID.randomUUID()}"

  @Provides
  @Singleton
  fun provideEventHandler(): EventHandler =
    when (EnvironmentVariables.eventProvider.get()) {
      EventProvider.ZENOH ->
        ZenohEventHandler().apply {
          // Subscribe to global and peripheral events
          subscribe(GLOBAL_SOURCE)
          subscribe(PERIPHERAL_SOURCE)
        }
    }

  @Provides
  @Singleton
  fun providePersistentContext(): Context? =
    when (EnvironmentVariables.contextProvider.get()) {
      PersistentContextProvider.ETCD -> {
        val url = EnvironmentVariables.etcdContextUrl.get() ?: return null

        EtcdContext(listOf(url)).apply {
          logger.info { "awaiting etcd connection..." }

          // Wait for the connection to be established
          awaitReady(Cirrina.ETCD_CONNECTION_TIMEOUT)
        }
      }
    }

  @Provides
  @Singleton
  fun provideMeterRegistry(): MeterRegistry {
    val compositeRegistry = CompositeMeterRegistry()

    EnvironmentVariables.influxMetricUrl.get()?.let { url ->
      compositeRegistry.add(createInfluxRegistry(url))
    }

    return compositeRegistry.apply {
      ClassLoaderMetrics().bindTo(this)
      JvmMemoryMetrics().bindTo(this)
      JvmGcMetrics().bindTo(this)
      JvmThreadMetrics().bindTo(this)
      ProcessorMetrics().bindTo(this)
    }
  }

  @Provides
  @Singleton
  fun provideTracing(
    @Named("identifier") identifier: String,
    meterRegistry: MeterRegistry,
  ): ObservationRegistry {
    val observationRegistry = ObservationRegistry.create()

    val traceUrl = EnvironmentVariables.zipkinTraceUrl.get() ?: return observationRegistry

    val zipkinExporter = ZipkinSpanExporter.builder().setEndpoint(traceUrl).build()

    val sdkTracerProvider =
      SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor.builder(zipkinExporter).build())
        .setResource(
          Resource.getDefault()
            .toBuilder()
            .put(ServiceAttributes.SERVICE_NAME, identifier)
            .put(ServiceAttributes.SERVICE_VERSION, getBuildVersion())
            .build()
        )
        .build()

    val openTelemetry =
      OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build()

    val otelTracer = openTelemetry.getTracer("io.micrometer.tracing")
    val otelCurrentTraceContext = OtelCurrentTraceContext()
    val baggageManager = OtelBaggageManager(otelCurrentTraceContext, emptyList(), emptyList())

    val otelTracerBridge = OtelTracer(otelTracer, otelCurrentTraceContext, {}, baggageManager)

    return observationRegistry.apply {
      observationConfig()
        .observationHandler(
          ObservationHandler.FirstMatchingCompositeObservationHandler(
            DefaultTracingObservationHandler(otelTracerBridge),
            DefaultMeterObservationHandler(meterRegistry),
          )
        )
    }
  }

  @Provides @CsmMain fun provideCsmMain(): URI = URI(EnvironmentVariables.csmMainUri.get())

  @Provides
  @Singleton
  fun provideServiceImplementationSelector(): ServiceImplementationSelector =
    URI(EnvironmentVariables.csmServiceBindingsUri.get())
      .let(CsmParser::parseServiceImplementationBindings)
      .let { ServiceImplementationBuilder.from(it.bindings).build().getOrThrow() }
      .let(::RandomServiceImplementationSelector)

  private fun createInfluxRegistry(url: String): InfluxMeterRegistry {
    val config =
      object : InfluxConfig {
        override fun step(): Duration =
          Duration.ofMillis(EnvironmentVariables.influxMetricStep.get())

        override fun uri(): String = url

        override fun org(): String = EnvironmentVariables.influxMetricOrg.get()

        override fun bucket(): String = EnvironmentVariables.influxMetricBucket.get()

        override fun token(): String = EnvironmentVariables.influxMetricToken.get()

        override fun get(k: String): String? = null
      }

    return InfluxMeterRegistry(config, Clock.SYSTEM)
  }
}
