package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.provider.EventHandlerZenoh

class ZenohEventHandlerTest : EventHandlerTest() {
  override fun createEventHandler(): EventHandler = EventHandlerZenoh()
}
