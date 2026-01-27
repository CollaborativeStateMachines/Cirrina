package at.ac.uibk.dps.cirrina.di

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import dagger.Component

@Component(modules = [TestModule::class])
interface TestComponent {
  fun runtime(): Runtime
}
