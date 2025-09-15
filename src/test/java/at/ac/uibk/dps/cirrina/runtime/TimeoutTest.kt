package at.ac.uibk.dps.cirrina.runtime

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.service.OptimalServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.utils.TestUtils.loggingOpenTelemetry
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockPersistentContext
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class TimeoutTest {

  @Test
  fun testTimeoutExecute() {
    // Must finish within five seconds
    assertTimeout(Duration.ofSeconds(5)) {
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
        val mockPersistentContext =
          mockPersistentContext(
            createBlock = { create("v", 0) },
            assignBlock = { superAssign, name, value ->
              assertEquals("v", name)
              assertTrue(value is Int)

              superAssign(name, value)
            },
          )

        // Create a map from service types to service implementations
        val services = ServiceImplementationBuilder.from(listOf()).build()
        val serviceImplementationSelector = OptimalServiceImplementationSelector(services)

        // Create and run the runtime using one state machine (stateMachine1)
        Runtime(
            loggingOpenTelemetry(),
            serviceImplementationSelector,
            mockEventHandler,
            mockPersistentContext,
          )
          .run(DefaultDescriptions.timeout, listOf("stateMachine1"))

        // This test counts up to 10, so the final value should be 10
        assertEquals(10, mockPersistentContext["v"])
      }
    }
  }
}
