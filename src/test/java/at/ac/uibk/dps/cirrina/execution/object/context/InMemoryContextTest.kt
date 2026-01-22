package at.ac.uibk.dps.cirrina.execution.`object`.context

class InMemoryContextTest : ContextTest() {
  override fun createContext(): Context = InMemoryContext(true)
}
