package at.ac.uibk.dps.cirrina.execution.`object`

abstract class EventHandlerTest {

  /*fun createEventHandler(): EventHandler = EventHandler()

  @ParameterizedTest
  @EnumSource(Csml.EventChannel::class)
  fun testEventHandlerSendReceive(channel: Csml.EventChannel) {
    createEventHandler().use { eventHandler ->
      val count = 5

      val context = ContextInMemory()
      val extent = Extent.of(context)

      val receivedEvents = CopyOnWriteArrayList<Event>()
      var latch = CountDownLatch(count)

      eventHandler.registerHandler {
        receivedEvents.add(it)
        latch.countDown()
      }

      // Send <count> events
      repeat(count) { i ->
        val event =
          Event.from(Csml.EventDescription("e1", channel, mapOf("varName" to "$i")))
            .evaluateData(extent)
            .copy(source = "source")

        eventHandler.send(event)
      }

      // External and global events should be received
      if (channel == Csml.EventChannel.EXTERNAL || channel == Csml.EventChannel.GLOBAL) {
        val success = latch.await(5, TimeUnit.SECONDS)
        Assertions.assertTrue(success, "timed out waiting for external or global events")
        Assertions.assertEquals(count, receivedEvents.size)
        receivedEvents.forEachIndexed { index, event ->
          val variable = event.data.first()
          assertEquals("varName", variable.name, "event data mismatch at index $index (name)")
          assertEquals(index, variable.value, "event data mismatch at index $index (value)")
        }
      } else {
        Thread.sleep(500)
        Assertions.assertEquals(0, receivedEvents.size)
      }

      // Clear and unsubscribe
      receivedEvents.clear()
      //      eventHandler.unsubscribe("source")

      latch = CountDownLatch(count)

      // Send <count> events
      repeat(count) { i ->
        val event =
          Event.from(Csml.EventDescription("e1", channel, mapOf("varName" to "$i")))
            .evaluateData(extent)
            .copy(source = "source")

        eventHandler.send(event)
      }

      // Only global events should be received
      if (channel == Csml.EventChannel.GLOBAL) {
        val success = latch.await(5, TimeUnit.SECONDS)
        Assertions.assertTrue(success, "timed out waiting for global events")
        Assertions.assertEquals(count, receivedEvents.size)
      } else {
        Thread.sleep(500)
        Assertions.assertEquals(
          0,
          receivedEvents.size,
          "events received for internal or external channel after unsubscribe",
        )
      }
    }
  }*/
}
