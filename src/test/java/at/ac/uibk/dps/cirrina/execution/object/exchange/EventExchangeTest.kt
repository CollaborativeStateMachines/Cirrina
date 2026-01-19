package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class EventExchangeTest {

  @Test
  fun testToFromBytes() {
    val contextVariable = ContextVariable("varName", "some string")

    assertDoesNotThrow {
      val eventOut = Event("name", EventChannel.EXTERNAL, listOf(contextVariable))
      val data = EventExchange(eventOut).toBytes().getOrThrow()

      val eventIn = EventExchange.fromBytes(data).getOrThrow().event

      assertEquals(eventOut.id, eventIn.id)
      assertEquals("name", eventIn.name)
      assertEquals("EXTERNAL", eventIn.channel.name)

      val firstVar = eventIn.data.first()
      assertEquals("varName", firstVar.name)
      assertEquals("some string", firstVar.value)
      assertFalse(firstVar.isLazy)
    }
  }
}
