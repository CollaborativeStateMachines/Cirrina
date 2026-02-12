package at.ac.uibk.dps.cirrina.cirrina.di

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.PersistentContextProvider
import at.ac.uibk.dps.cirrina.execution.`object`.ActionCommandFactory
import at.ac.uibk.dps.cirrina.execution.`object`.ActionCommandFactoryImpl
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.provider.ContextEtcd
import at.ac.uibk.dps.cirrina.util.getBuildVersion
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
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.net.URI
import java.time.Duration
import java.util.UUID

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class Identifier

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class Main

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class Run

@Module
class CirrinaModule {

  @Provides @Singleton fun provideEventHandler(): EventHandler = EventHandler()

  @Provides
  @Singleton
  fun providePersistentContext(): Context? =
    when (EnvironmentVariables.contextProvider.get()) {
      PersistentContextProvider.ETCD -> {
        val url = EnvironmentVariables.etcdContextUrl.get() ?: return null

        ContextEtcd(listOf(url))
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
  fun provideObservationRegistry(
    @Identifier identifier: String,
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

  @Provides @Singleton @Identifier fun provideIdentifier(): String = "cirrina.${UUID.randomUUID()}"

  @Provides @Singleton @Main fun provideMain(): URI = URI(EnvironmentVariables.mainUri.get())

  @Provides @Singleton @Run fun provideRun(): List<String> = EnvironmentVariables.run.get()

  @Provides
  @Singleton
  fun provideActionCommandFactory(meterRegistry: MeterRegistry): ActionCommandFactory =
    ActionCommandFactoryImpl(meterRegistry)

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
