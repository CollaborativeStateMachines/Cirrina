package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.di.Identifier
import at.ac.uibk.dps.cirrina.cirrina.di.Main
import at.ac.uibk.dps.cirrina.cirrina.di.Run
import at.ac.uibk.dps.cirrina.execution.`object`.ActionCommandFactory
import at.ac.uibk.dps.cirrina.execution.`object`.ActionCommandFactoryImpl
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import dagger.Module
import dagger.Provides
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import jakarta.inject.Singleton
import java.net.URI
import java.util.UUID

@Module
class TestModule(
  private val context: Context,
  private val mainUri: URI,
  private val run: List<String>,
) {
  @Provides fun provideContext() = context

  @Provides fun provideMeterRegistry(): MeterRegistry = SimpleMeterRegistry()

  @Provides fun provideObservationRegistry(): ObservationRegistry = ObservationRegistry.create()

  @Provides @Singleton @Identifier fun provideIdentifier(): String = "cirrina.${UUID.randomUUID()}"

  @Provides @Main fun provideMain() = mainUri

  @Provides @Run fun provideRun() = run

  @Provides
  @Singleton
  fun provideActionCommandFactory(meterRegistry: MeterRegistry): ActionCommandFactory =
    ActionCommandFactoryImpl(meterRegistry)
}
