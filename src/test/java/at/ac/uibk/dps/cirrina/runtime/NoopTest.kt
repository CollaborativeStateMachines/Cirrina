package at.ac.uibk.dps.cirrina.runtime

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.utils.TestUtils.loggingOpenTelemetry
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockPersistentContext
import java.time.Duration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class NoopTest {

  @Test
  fun testNoopExecute() {
    // Must finish within five seconds
    assertTimeout(Duration.ofSeconds(5)) {
      // Should not throw any exception
      assertDoesNotThrow {
        // Mock the event handler
        val mockEventHandler =
          object : EventHandler() {
            override fun close() {}

            override fun sendEvent(event: Event, source: String?) = propagateEvent(event)

            override fun subscribe(topic: String) {}

            override fun unsubscribe(topic: String) {}

            override fun subscribe(source: String, subject: String) {}

            override fun unsubscribe(source: String, subject: String) {}
          }

        // Mock the persistent context
        val mockPersistentContext = mockPersistentContext()

        // Create a map from service types to service implementations
        val services = ServiceImplementationBuilder.from(listOf()).build()
        val serviceImplementationSelector = RandomServiceImplementationSelector(services)

        // Create and run the runtime using a single state machine.
        Runtime(
            DefaultDescriptions.noop,
            listOf("stateMachine"),
            loggingOpenTelemetry(),
            serviceImplementationSelector,
            mockEventHandler,
            mockPersistentContext,
          )
          .run()
      }
    }
  }
}
