package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class EventExchangeTest {

  @Test
  fun testToFromBytes() {
    val originalEvent =
      Event("name", EventChannel.EXTERNAL, listOf(ContextVariable("varName", "some string")))

    // Perform round-trip and verify the event properties
    originalEvent.roundTrip().let { receivedEvent ->
      assertEquals(originalEvent.id, receivedEvent.id)
      assertEquals("name", receivedEvent.name)
      assertEquals(EventChannel.EXTERNAL, receivedEvent.channel)

      // Verify nested data
      receivedEvent.data.first().run {
        assertEquals("varName", name)
        assertEquals("some string", value)
        assertFalse(isLazy)
      }
    }
  }

  private fun Event.roundTrip(): Event =
    EventExchange(this).toBytes().getOrThrow().let { bytes ->
      EventExchange.fromBytes(bytes).getOrThrow().event
    }
}
