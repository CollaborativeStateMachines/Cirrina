package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.cirrina.di.CirrinaModule
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import dagger.Component
import jakarta.inject.Singleton

@Singleton
@Component(modules = [CirrinaModule::class])
interface CirrinaComponent {
  fun runtime(): Runtime

  fun eventHandler(): EventHandler

  fun persistentContext(): Context
}
