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
import jakarta.inject.Qualifier
import java.net.URI
import javax.inject.Singleton
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class CsmMain

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class CsmStateMachineNames

@Module
class CirrinaModule {

  @Provides @CsmMain fun provideCsmMain(): URI = URI(EnvironmentVariables.appPath.get())

  @Provides
  @CsmStateMachineNames
  fun provideCsmStateMachineNames(): List<String> = EnvironmentVariables.instantiate.get()

  @Provides
  @Singleton
  fun provideEventHandler(): EventHandler =
    when (EnvironmentVariables.eventProvider.get()) {
      EventProvider.NATS ->
        NatsEventHandler(EnvironmentVariables.natsEventUrl.get()).apply {
          logger.info { "awaiting nats connection..." }

          // Wait for the connection to be established
          awaitReady(Cirrina.NATS_CONNECTION_TIMEOUT)

          // Subscribe to all events
          subscribe(NatsEventHandler.GLOBAL_SOURCE, "*")
          subscribe(NatsEventHandler.PERIPHERAL_SOURCE, "*")
        }
    }

  @Provides
  @Singleton
  fun provideServiceImplementationSelector(): ServiceImplementationSelector =
    URI(EnvironmentVariables.serviceBindingsPath.get())
      .let(CsmParser::parseServiceImplementationBindings)
      .let { ServiceImplementationBuilder.from(it.bindings).build().getOrThrow() }
      .let(::RandomServiceImplementationSelector)

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
}
