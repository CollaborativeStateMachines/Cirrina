package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.Runtime
import at.ac.uibk.dps.cirrina.cirrina.di.CirrinaModule
import at.ac.uibk.dps.cirrina.cirrina.di.Identifier
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import dagger.Component
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Singleton

@Singleton
@Component(modules = [CirrinaModule::class])
interface CirrinaComponent {
  fun eventHandler(): EventHandler

  fun persistentContext(): Context?

  fun meterRegistry(): MeterRegistry

  fun runtime(): Runtime

  @Identifier fun identifier(): String
}
