package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandlerTest

class EventHandlerZenohTest : EventHandlerTest() {
  override fun createEventHandler(): EventHandler = EventHandlerZenoh()
}
