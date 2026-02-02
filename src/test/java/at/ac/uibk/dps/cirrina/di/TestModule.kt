package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.di.CsmMain
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import dagger.Module
import dagger.Provides
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import java.net.URI

@Module
class TestModule(
  private val eventHandler: EventHandler,
  private val context: Context,
  private val selector: ServiceImplementationSelector,
  private val mainUri: URI,
) {
  @Provides fun provideEventHandler() = eventHandler

  @Provides fun provideContext() = context

  @Provides fun provideSelector() = selector

  @Provides @CsmMain fun provideUri() = mainUri

  @Provides fun provideMeterRegistry(): MeterRegistry = SimpleMeterRegistry()

  @Provides fun provideObservationRegistry(): ObservationRegistry = ObservationRegistry.create()
}
