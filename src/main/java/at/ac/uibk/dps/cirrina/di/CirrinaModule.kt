package at.ac.uibk.dps.cirrina.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.Cirrina
import at.ac.uibk.dps.cirrina.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.cirrina.EventProvider
import at.ac.uibk.dps.cirrina.cirrina.PersistentContextProvider
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.EtcdContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.NatsEventHandler
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.CsmParser
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
import io.micrometer.influx.InfluxConfig
import io.micrometer.influx.InfluxMeterRegistry
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.net.URI
import java.time.Duration
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class CsmMain

@Module
class CirrinaModule {

  @Provides
  @Singleton
  fun provideEventHandler(): EventHandler =
    when (EnvironmentVariables.eventProvider.get()) {
      EventProvider.NATS ->
        NatsEventHandler(EnvironmentVariables.natsEventUrl.get()).apply {
          logger.info { "awaiting nats connection..." }

          // Wait for the connection to be established
          awaitReady(Cirrina.NATS_CONNECTION_TIMEOUT)

          // Subscribe to global and peripheral events
          subscribe(NatsEventHandler.GLOBAL_SOURCE)
          subscribe(NatsEventHandler.PERIPHERAL_SOURCE)
        }
    }

  @Provides
  @Singleton
  fun providePersistentContext(): Context =
    when (EnvironmentVariables.contextProvider.get()) {
      PersistentContextProvider.ETCD ->
        EtcdContext(listOf(EnvironmentVariables.etcdContextUrl.get())).apply {
          logger.info { "awaiting etcd connection..." }

          // Wait for the connection to be established
          awaitReady(Cirrina.ETCD_CONNECTION_TIMEOUT)
        }
    }

  @Provides
  @Singleton
  fun provideMeterRegistry(): MeterRegistry {
    val compositeRegistry = CompositeMeterRegistry()

    compositeRegistry.add(createInfluxRegistry(EnvironmentVariables.influxMetricUrl.get()))

    ClassLoaderMetrics().bindTo(compositeRegistry)
    JvmMemoryMetrics().bindTo(compositeRegistry)
    JvmGcMetrics().bindTo(compositeRegistry)
    JvmThreadMetrics().bindTo(compositeRegistry)
    ProcessorMetrics().bindTo(compositeRegistry)

    return compositeRegistry
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
