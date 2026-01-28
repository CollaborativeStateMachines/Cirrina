package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class NatsEventHandlerTest {

  @ParameterizedTest
  @EnumSource(EventChannel::class)
  fun testNatsEventHandlerSendReceive(channel: EventChannel) {
    val natsUrl = System.getenv("NATS_EVENT_URL")
    assumeTrue(natsUrl != null, "skipping nats event handler test")

    assertTimeout(Duration.ofSeconds(15)) {
      val count = 5
      val context = InMemoryContext()
      val extent = Extent.of(context)
      val receivedEvents = CopyOnWriteArrayList<Event>()

      var latch = CountDownLatch(count)

      val handlerListener =
        object : EventListener {
          override fun onReceiveEvent(event: Event) {
            receivedEvents.add(event)
            latch.countDown()
          }
        }

      val eventDescription = Csml.EventDescription("e1", channel, mapOf("varName" to "5"))
      val event = EventBuilder.from(eventDescription).build().getOrThrow()

      NatsEventHandler(natsUrl).use { handler ->
        assertTrue(handler.awaitReady(), "nats handler failed to connect")
        handler.listener = handlerListener

        handler.subscribe("global")
        handler.subscribe("source")

        // Send <count> events
        repeat(count) {
          handler.sendEvent(Event.ensureHasEvaluatedData(event, extent).withSource("source"))
        }

        // External and global events should be received
        if (channel == EventChannel.EXTERNAL || channel == EventChannel.GLOBAL) {
          val success = latch.await(5, TimeUnit.SECONDS)
          assertTrue(success, "timed out waiting for external or global events")
          assertEquals(count, receivedEvents.size)
        } else {
          Thread.sleep(500)
          assertEquals(0, receivedEvents.size)
        }

        // Clear and unsubscribe
        receivedEvents.clear()
        handler.unsubscribe("source")

        latch = CountDownLatch(count)

        // Send <count> events
        repeat(count) {
          handler.sendEvent(Event.ensureHasEvaluatedData(event, extent).withSource("source"))
        }

        // Only global events should be received
        if (channel == EventChannel.GLOBAL) {
          val success = latch.await(5, TimeUnit.SECONDS)
          assertTrue(success, "timed out waiting for global events")
          assertEquals(count, receivedEvents.size)
        } else {
          Thread.sleep(500)
          assertEquals(
            0,
            receivedEvents.size,
            "events received for internal or external channel after unsubscribe",
          )
        }
      }
    }
  }
}
