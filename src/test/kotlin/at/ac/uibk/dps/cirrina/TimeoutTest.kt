package at.ac.uibk.dps.cirrina

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

class TimeoutTest {

  @Test
  fun testTimeoutExecute() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val context = ContextInMemory()

        val runtime =
          DaggerTestComponent.builder()
            .testModule(TestModule(context, DefaultDescriptions.timeout, listOf("timeout")))
            .build()
            .runtime()

        val duration = measureTime { runtime.run() }
        println("timeout execution: $duration")

        assertEquals(10, context.get("v"))
      }
    }
  }
}
