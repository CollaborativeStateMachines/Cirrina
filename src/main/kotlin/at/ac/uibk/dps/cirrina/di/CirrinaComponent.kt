package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.Runtime
import at.ac.uibk.dps.cirrina.cirrina.di.CirrinaModule
import at.ac.uibk.dps.cirrina.cirrina.di.Identifier
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import dagger.Component
import io.dropwizard.metrics5.MetricRegistry
import jakarta.inject.Singleton

@Singleton
@Component(modules = [CirrinaModule::class])
interface CirrinaComponent {
  fun persistentContext(): Context?

  fun metricRegistry(): MetricRegistry

  fun runtime(): Runtime

  @Identifier fun identifier(): String
}
