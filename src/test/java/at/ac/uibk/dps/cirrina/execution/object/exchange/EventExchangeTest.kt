package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventExchangeTest {

  @Test
  fun testToFromBytes() {
    val originalEvent =
      Event(
        "topic",
        EventChannel.EXTERNAL,
        listOf(ContextVariable("varName", "some string")),
        "source",
        "id",
        1,
      )

    // Verify the event properties
    assertEquals(originalEvent.topic, "topic")
    assertEquals(originalEvent.channel, EventChannel.EXTERNAL)
    assertEquals(originalEvent.data.size, 1)
    assertEquals(originalEvent.data[0].name, "varName")
    assertEquals(originalEvent.data[0].value, "some string")
    assertEquals(originalEvent.source, "source")
    assertEquals(originalEvent.id, "id")
    assertEquals(originalEvent.createdTime, 1)

    // Perform round-trip and verify the event properties
    originalEvent.roundTrip().let { receivedEvent ->
      assertEquals(originalEvent.topic, receivedEvent.topic)
      assertEquals(originalEvent.channel, receivedEvent.channel)
      assertEquals(originalEvent.data, receivedEvent.data)
      assertEquals(originalEvent.id, receivedEvent.id)
      assertEquals(originalEvent.createdTime, receivedEvent.createdTime)
    }
  }

  private fun Event.roundTrip(): Event =
    EventExchange(this).toBytes().getOrThrow().let { bytes ->
      EventExchange.fromBytes(bytes).getOrThrow().event
    }
}
