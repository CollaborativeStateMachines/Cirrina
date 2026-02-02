package at.ac.uibk.dps.cirrina.execution.`object`.event

class ZenohEventHandlerTest : EventHandlerTest() {
  override fun createEventHandler(): EventHandler = ZenohEventHandler(listOf())
}
