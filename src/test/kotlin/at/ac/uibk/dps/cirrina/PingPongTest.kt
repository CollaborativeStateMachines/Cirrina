package at.ac.uibk.dps.cirrina

import EventHandlerInMemory
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class PingPongTest {

  @Test
  fun testPingPongExecute() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val eventHandler = EventHandlerInMemory()
        val context = ContextInMemory()

        val runtime =
          DaggerTestComponent.builder()
            .testModule(TestModule(eventHandler, context, DefaultDescriptions.pingPong))
            .build()
            .runtime()

        val duration = measureTime { runtime.run() }
        println("pingPong execution: $duration")

        assertEquals(100_000, context.get("v"))
      }
    }
  }
}
