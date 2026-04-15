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

class LoopTest {
  @Test
  fun testLoopExecute() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val context = ContextInMemory()

        val runtime =
          DaggerTestComponent.builder()
            .testModule(TestModule(context, DefaultDescriptions.loop, listOf("one", "two")))
            .build()
            .runtime()

        val duration = measureTime { runtime.run() }
        println("loop execution: $duration")

        assertEquals(1, context.get("v"))
      }
    }
  }
}
