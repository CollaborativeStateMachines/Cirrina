package at.ac.uibk.dps.cirrina.cirrina.di

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.PersistentContextProvider
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.provider.ContextEtcd
import com.codahale.metrics.CsvReporter
import com.codahale.metrics.MetricRegistry
import dagger.Module
import dagger.Provides
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.dropwizard.DropwizardConfig
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry
import io.micrometer.core.instrument.util.HierarchicalNameMapper
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.TimeUnit

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class Identifier

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class Main

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class Run

@Module
class CirrinaModule {

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
  fun provideMeterRegistry(): MetricRegistry =
    MetricRegistry().apply {
      val path = Paths.get(EnvironmentVariables.metricsDirectory.get()).toAbsolutePath()
      Files.createDirectories(path)

      CsvReporter.forRegistry(this)
        .build(path.toFile())
        .start(EnvironmentVariables.metricsPeriod.get(), TimeUnit.SECONDS)

      object :
          DropwizardMeterRegistry(
            object : DropwizardConfig {
              override fun get(key: String): String? = null

              override fun prefix(): String = ""
            },
            this,
            HierarchicalNameMapper.DEFAULT,
            Clock.SYSTEM,
          ) {
          override fun nullGaugeValue(): Double = Double.NaN
        }
        .apply {
          ProcessorMetrics().bindTo(this)
          JvmMemoryMetrics().bindTo(this)
          JvmGcMetrics().bindTo(this)
        }
    }

  @Provides @Singleton @Identifier fun provideIdentifier(): String = "cirrina.${UUID.randomUUID()}"

  @Provides @Singleton @Main fun provideMain(): URI = URI(EnvironmentVariables.mainUri.get())

  @Provides @Singleton @Run fun provideRun(): List<String> = EnvironmentVariables.run.get()
}
