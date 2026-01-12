package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import java.time.Duration
import java.util.concurrent.CountDownLatch
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class NatsEventHandlerTest {

  @ParameterizedTest
  @EnumSource(Csml.EventChannel::class)
  fun testNatsEventHandlerSendReceive(channel: Csml.EventChannel) {
    val natsServerURL = System.getenv("NATS_EVENT_URL")
    assumeTrue(natsServerURL != null, "Skipping NATS event handler test")

    // Test should finish in 5 seconds
    assertTimeout(Duration.ofSeconds(5)) {
      // Number of events sent
      val count = 5

      // Number of events to wait for
      val latch = CountDownLatch(count)

      val localContext = InMemoryContext(true)

      val eventListener =
        object : EventListener {
          val events = mutableListOf<Event>()

          override fun onReceiveEvent(event: Event?): Boolean {
            events.add(requireNotNull(event))
            latch.countDown()
            return true
          }
        }

      // Create an event
      val e1 = run {
        EventBuilder.from(Csml.EventDescription("e1", channel, mapOf("varName" to "5"))).build()
      }

      // Connect the event handler to the NATS server
      NatsEventHandler(natsServerURL).use { natsEventHandler ->
        // Expect it to connect
        assertTrue(natsEventHandler.awaitInitialConnection())

        // Create a listener
        natsEventHandler.addListener(eventListener)

        // Global and external events can be sent using the event handler
        if (channel == Csml.EventChannel.GLOBAL || channel == Csml.EventChannel.EXTERNAL) {
          // Subscribe to the event
          when (channel) {
            Csml.EventChannel.GLOBAL -> natsEventHandler.subscribe("e1")
            Csml.EventChannel.EXTERNAL -> natsEventHandler.subscribe("source", "e1")
            else -> {}
          }

          // Send multiple events
          repeat(count) {
            natsEventHandler.sendEvent(
              Event.ensureHasEvaluatedData(e1, Extent(localContext)),
              "source",
            )
          }

          // Wait for the events to arrive
          latch.await(10, java.util.concurrent.TimeUnit.SECONDS)

          // Check if the events were received correctly
          assertEquals(5, eventListener.events.size)
          eventListener.events.forEach { e ->
            assertEquals("e1", e.getName())
            assertEquals(channel, e.getChannel())
            assertEquals(1, e.getData().size)

            val data = e.getData().first()
            assertEquals("varName", data.name)
            assertEquals(5, data.value)
            assertFalse(data.isLazy)
          }

          // Clear the events
          eventListener.events.clear()

          // Unsubscribe from the event
          when (channel) {
            Csml.EventChannel.GLOBAL -> natsEventHandler.unsubscribe("e1")
            Csml.EventChannel.EXTERNAL -> natsEventHandler.unsubscribe("source", "e1")
            else -> {}
          }

          // Send multiple events again
          repeat(count) {
            natsEventHandler.sendEvent(
              Event.ensureHasEvaluatedData(e1, Extent(localContext)),
              "source",
            )
          }

          // No events should arrive now
          assertEquals(0, eventListener.events.size)
        }
        // All other event types cannot be sent
        else {
          assertDoesNotThrow {
            // Send multiple events
            repeat(count) {
              natsEventHandler.sendEvent(
                Event.ensureHasEvaluatedData(e1, Extent(localContext)),
                "source",
              )
            }

            // No events should arrive
            assertEquals(0, eventListener.events.size)
          }
        }
      }
    }
  }
}
