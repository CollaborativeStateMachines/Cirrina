package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import dagger.Component
import jakarta.inject.Singleton

@Singleton
@Component(modules = [TestModule::class])
interface TestComponent {
  fun runtime(): Runtime
}
