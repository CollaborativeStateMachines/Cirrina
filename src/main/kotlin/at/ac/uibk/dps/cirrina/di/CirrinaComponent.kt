package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.Runtime
import at.ac.uibk.dps.cirrina.cirrina.di.CirrinaModule
import at.ac.uibk.dps.cirrina.cirrina.di.Identifier
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.trace.CausalTraceRecorderModule
import dagger.Component
import jakarta.inject.Singleton

@Singleton
@Component(modules = [CirrinaModule::class, CausalTraceRecorderModule::class])
interface CirrinaComponent {
  fun persistentContext(): Context?

  fun runtime(): Runtime

  @Identifier fun identifier(): String
}
