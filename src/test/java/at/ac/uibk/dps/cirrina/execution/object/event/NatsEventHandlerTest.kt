package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import java.time.Duration
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
    assumeTrue(natsUrl != null, "Skipping NATS event handler test")

    assertTimeout(Duration.ofSeconds(5)) {
      val count = 5
      val latch = CountDownLatch(count)
      val context = InMemoryContext(true)
      val extent = Extent.of(context)

      // Collects events and counts down the latch
      val receivedEvents = mutableListOf<Event>()
      val handlerListener =
        object : EventListener {
          override fun onReceiveEvent(event: Event) {
            receivedEvents.add(event)
            latch.countDown()
          }
        }

      val event =
        EventBuilder.from(Csml.EventDescription("e1", channel, mapOf("varName" to "5")))
          .build()
          .getOrThrow()

      NatsEventHandler(natsUrl).use { handler ->
        assertTrue(handler.awaitReady(), "NATS handler failed to connect")
        handler.listener = handlerListener

        if (channel in setOf(EventChannel.GLOBAL, EventChannel.EXTERNAL)) {
          // Scope helper to manage subscriptions and event validation
          handler.run {
            manageSubscription(channel, "e1", "source", subscribe = true)

            repeat(count) { sendEvent(Event.ensureHasEvaluatedData(event, extent), "source") }

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Timed out waiting for events")
            assertEquals(count, receivedEvents.size)

            receivedEvents.forEach { e ->
              assertEquals("e1", e.name)
              assertEquals(channel, e.channel)
              e.data.first().run {
                assertEquals("varName", name)
                assertEquals(5, value)
                assertFalse(isLazy)
              }
            }

            receivedEvents.clear()
            manageSubscription(channel, "e1", "source", subscribe = false)

            repeat(count) { sendEvent(Event.ensureHasEvaluatedData(event, extent), "source") }

            assertEquals(0, receivedEvents.size, "Events received after unsubscribe")
          }
        } else {
          // Ensure non-routable channels don't trigger listeners
          repeat(count) { handler.sendEvent(Event.ensureHasEvaluatedData(event, extent), "source") }
          assertEquals(0, receivedEvents.size)
        }
      }
    }
  }

  private fun NatsEventHandler.manageSubscription(
    channel: EventChannel,
    name: String,
    source: String,
    subscribe: Boolean,
  ) {
    when (channel) {
      EventChannel.GLOBAL -> if (subscribe) subscribe(name) else unsubscribe(name)
      EventChannel.EXTERNAL -> if (subscribe) subscribe(source, name) else unsubscribe(source, name)
      else -> {}
    }
  }
}
