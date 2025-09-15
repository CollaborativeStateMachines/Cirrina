package at.ac.uibk.dps.cirrina.runtime

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.csm.description.HttpServiceImplementationDescription
import at.ac.uibk.dps.cirrina.csm.description.ServiceImplementationDescription
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.service.OptimalServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.utils.TestUtils.loggingOpenTelemetry
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockHttpServer
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockPersistentContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout
import java.time.Duration

class CompleteTest {

  @Test
  fun testCompleteExecute() {
    // Must finish within five seconds
    assertTimeout(Duration.ofSeconds(10)) {
      // Should not throw any exception
      assertDoesNotThrow {
        // Mock the event handler
        val mockEventHandler = object : EventHandler() {
          override fun close() {}
          override fun sendEvent(event: Event, source: String) = propagateEvent(event)
          override fun subscribe(topic: String) {}
          override fun unsubscribe(topic: String) {}
          override fun subscribe(source: String, subject: String) {}
          override fun unsubscribe(source: String, subject: String) {}
        }

        // Mock the persistent context
        val mockPersistentContext = mockPersistentContext(
          assignBlock = { superAssign, name, value ->
            assertEquals("v", name)
            assertTrue(value is Int)
            superAssign(name, value)
          }
        )

        // Mock the HTTP server
        val server = mockHttpServer { input ->
          val v = input.firstOrNull { it.name == "v" }
            ?: error("Variable 'v' not found")

          listOf(ContextVariable("v", (v.value as Int) + 1))
        }

        // Create a map from service types to service implementations
        val service = HttpServiceImplementationDescription(
          "increment",
          1.0,
          true,
          ServiceImplementationDescription.Type.HTTP,
          "http",
          "localhost",
          8000,
          "/increment",
          HttpServiceImplementationDescription.Method.GET
        )

        val services = ServiceImplementationBuilder.from(listOf(service)).build()
        val serviceImplementationSelector = OptimalServiceImplementationSelector(services)

        // Create and run the runtime using two state machines (stateMachine1 and stateMachine2)
        Runtime(
          loggingOpenTelemetry(),
          serviceImplementationSelector,
          mockEventHandler,
          mockPersistentContext
        ).run(DefaultDescriptions.complete, listOf("stateMachine1"))

        // This test counts up to 100, and down to 0, so the final value should be 0
        assertEquals(0, mockPersistentContext["v"])
        assertEquals(true, mockPersistentContext["b"])
      }
    }
  }
}
