package at.ac.uibk.dps.cirrina.runtime

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.utils.TestUtils.loggingOpenTelemetry
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockPersistentContext
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class PingPongTest {

  @Test
  fun testPingPongExecute() {
    // Must finish within a second
    assertTimeout(Duration.ofSeconds(1)) {
      // Should not throw any exception
      assertDoesNotThrow {
        // Mock the event handler
        val mockEventHandler =
          object : EventHandler() {
            override fun close() {}

            override fun sendEvent(event: Event, source: String) = propagateEvent(event)

            override fun subscribe(topic: String) {}

            override fun unsubscribe(topic: String) {}

            override fun subscribe(source: String, subject: String) {}

            override fun unsubscribe(source: String, subject: String) {}
          }

        // Mock the persistent context
        var nextV = 1
        val mockPersistentContext =
          mockPersistentContext(
            createBlock = { create("v", 0) },
            assignBlock = { superAssign, name, value ->
              assertEquals("v", name)
              assertTrue(value is Int)
              assertEquals(nextV++, value)
              assertTrue((value as Int) <= 100)
              superAssign(name, value)
            },
          )

        // Create and run the runtime using two state machines (stateMachine1 and stateMachine2)
        Runtime(loggingOpenTelemetry(), mockEventHandler, mockPersistentContext)
          .run(DefaultDescriptions.pingPong, listOf("stateMachine1", "stateMachine2"))

        // This test counts up to 100, so the final value should be 100
        assertEquals(100, mockPersistentContext["v"])
      }
    }
  }
}
