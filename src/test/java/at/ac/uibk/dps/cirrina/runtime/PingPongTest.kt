package at.ac.uibk.dps.cirrina.runtime

import InMemoryEventHandler
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.GLOBAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.PERIPHERAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class PingPongTest {

  @Test
  fun testPingPongExecute() {
    assertTimeout(Duration.ofSeconds(5)) {
      assertDoesNotThrow {
        val eventHandler =
          InMemoryEventHandler().apply {
            subscribe(GLOBAL_SOURCE)
            subscribe(PERIPHERAL_SOURCE)
          }
        val context = InMemoryContext()

        val selector =
          RandomServiceImplementationSelector(
            ServiceImplementationBuilder.from(emptyList()).build().getOrThrow()
          )

        val runtime =
          DaggerTestComponent.builder()
            .testModule(TestModule(eventHandler, context, selector, DefaultDescriptions.pingPong))
            .build()
            .runtime()

        val duration = measureTime { runtime.run() }
        println("pingPong execution: $duration")

        assertEquals(100_000, context.get("v"))
      }
    }
  }
}
