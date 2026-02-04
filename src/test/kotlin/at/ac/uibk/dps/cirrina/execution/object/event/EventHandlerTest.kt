package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.csm.Csml.EventDescription
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler.Companion.GLOBAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.`object`.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.Extent
import at.ac.uibk.dps.cirrina.execution.provider.InMemoryContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.use
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

abstract class EventHandlerTest {

  protected abstract fun createEventHandler(): EventHandler

  @ParameterizedTest
  @EnumSource(EventChannel::class)
  fun testEventHandlerSendReceive(channel: EventChannel) {
    createEventHandler().use { eventHandler ->
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

      eventHandler.listener = handlerListener

      eventHandler.subscribe(GLOBAL_SOURCE)
      eventHandler.subscribe("source")

      // Send <count> events
      repeat(count) { i ->
        val event =
          Event.from(EventDescription("e1", channel, mapOf("varName" to "$i")))
            .evaluateData(extent)
            .copy(source = "source")

        eventHandler.send(event)
      }

      // External and global events should be received
      if (channel == EventChannel.EXTERNAL || channel == EventChannel.GLOBAL) {
        val success = latch.await(5, TimeUnit.SECONDS)
        assertTrue(success, "timed out waiting for external or global events")
        assertEquals(count, receivedEvents.size)
        receivedEvents.forEachIndexed { index, event ->
          val variable = event.data.first()
          assertEquals("varName", variable.name, "event data mismatch at index $index (name)")
          assertEquals(index, variable.value, "event data mismatch at index $index (value)")
        }
      } else {
        Thread.sleep(500)
        assertEquals(0, receivedEvents.size)
      }

      // Clear and unsubscribe
      receivedEvents.clear()
      eventHandler.unsubscribe("source")

      latch = CountDownLatch(count)

      // Send <count> events
      repeat(count) { i ->
        val event =
          Event.from(EventDescription("e1", channel, mapOf("varName" to "$i")))
            .evaluateData(extent)
            .copy(source = "source")

        eventHandler.send(event)
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
