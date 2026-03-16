package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.di.Identifier
import at.ac.uibk.dps.cirrina.cirrina.di.Main
import at.ac.uibk.dps.cirrina.cirrina.di.Run
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import dagger.Module
import dagger.Provides
import io.dropwizard.metrics5.CsvReporter
import io.dropwizard.metrics5.MetricRegistry
import jakarta.inject.Singleton
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

@Module
class TestModule(
  private val context: Context,
  private val mainUri: URI,
  private val run: List<String>,
) {
  @Provides fun provideContext() = context

  @Provides
  @Singleton
  fun provideMeterRegistry(): MetricRegistry =
    MetricRegistry().apply {
      CsvReporter.forRegistry(this).build(File("")).start(1, TimeUnit.SECONDS)
    }

  @Provides @Singleton @Identifier fun provideIdentifier(): String = "cirrina.${UUID.randomUUID()}"

  @Provides @Main fun provideMain() = mainUri

  @Provides @Run fun provideRun() = run
}
