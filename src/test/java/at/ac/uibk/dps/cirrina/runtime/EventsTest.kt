package at.ac.uibk.dps.cirrina.runtime

import InMemoryEventHandler
import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.PERIPHERAL_SOURCE
import java.time.Duration
import kotlin.time.measureTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class EventsTest {

  @Test
  fun testEventsExecute() = runBlocking {
    assertDoesNotThrow {
      val eventHandler = InMemoryEventHandler()
      val context = InMemoryContext()

      val runtime =
        DaggerTestComponent.builder()
          .testModule(TestModule(eventHandler, context, DefaultDescriptions.events))
          .build()
          .runtime()

      val eventJob =
        launch(Dispatchers.Default) {
          delay(1000)
          eventHandler.send(
            Event.from(Csml.EventDescription("pe1", EventChannel.PERIPHERAL, mapOf()))
              .getOrThrow()
              .copy(source = PERIPHERAL_SOURCE)
          )
        }

      assertTimeout(Duration.ofSeconds(10)) {
        val duration = measureTime { runtime.run() }
        println("events execution: $duration")
      }

      eventJob.join()

      assertTrue(context.get("b") as Boolean)
      assertTrue(context.get("c") as Boolean)
    }
  }
}
