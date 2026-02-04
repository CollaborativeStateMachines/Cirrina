package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory

class InMemoryContextTest : ContextTest() {
  override fun createContext(): Context = ContextInMemory()
}
