package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.ContextTest

class ContextInMemoryTest : ContextTest() {
  override fun createContext(): Context = ContextInMemory()
}
