package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.cirrina.di.CirrinaModule
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import dagger.Component
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
@Component(modules = [CirrinaModule::class])
interface CirrinaComponent {

  @Named("identifier") fun identifier(): String

  fun eventHandler(): EventHandler

  fun persistentContext(): Context

  fun meterRegistry(): MeterRegistry

  fun runtime(): Runtime
}
