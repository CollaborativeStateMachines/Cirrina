package at.ac.uibk.dps.cirrina

import EventHandlerInMemory
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.util.TestUtils.mockHttpServer
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class InvokeTest {

  @Test
  fun testInvokeExecute() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val eventHandler = EventHandlerInMemory()
        val context = ContextInMemory()

        val server = mockHttpServer { input ->
          val v = input.firstOrNull { it.name == "v" } ?: error("variable 'v' not found")
          listOf(ContextVariable("v", (v.value as Int) + 1))
        }

        try {
          val runtime =
            DaggerTestComponent.builder()
              .testModule(TestModule(eventHandler, context, DefaultDescriptions.invoke))
              .build()
              .runtime()

          val duration = measureTime { runtime.run() }
          println("invoke execution: $duration")

          assertEquals(10, context.get("v"))
        } finally {
          server.stop(1)
        }
      }
    }
  }
}
