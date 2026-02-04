package at.ac.uibk.dps.cirrina.runtime

import InMemoryEventHandler
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class NoopTest {

  @Test
  fun testNoopExecute() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val eventHandler = InMemoryEventHandler()
        val context = InMemoryContext()

        val runtime =
          DaggerTestComponent.builder()
            .testModule(TestModule(eventHandler, context, DefaultDescriptions.noop))
            .build()
            .runtime()

        val duration = measureTime { runtime.run() }
        println("noop execution: $duration")
      }
    }
  }
}
