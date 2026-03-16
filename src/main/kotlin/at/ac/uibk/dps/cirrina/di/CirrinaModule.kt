package at.ac.uibk.dps.cirrina.cirrina.di

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.PersistentContextProvider
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.provider.ContextEtcd
import dagger.Module
import dagger.Provides
import io.dropwizard.metrics5.CsvReporter
import io.dropwizard.metrics5.MetricRegistry
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.net.URI
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
      CsvReporter.forRegistry(this)
        .build(Paths.get(EnvironmentVariables.metricsDirectory.get()).toAbsolutePath().toFile())
        .start(EnvironmentVariables.metricsPeriod.get(), TimeUnit.SECONDS)
    }

  @Provides @Singleton @Identifier fun provideIdentifier(): String = "cirrina.${UUID.randomUUID()}"

  @Provides @Singleton @Main fun provideMain(): URI = URI(EnvironmentVariables.mainUri.get())

  @Provides @Singleton @Run fun provideRun(): List<String> = EnvironmentVariables.run.get()
}
