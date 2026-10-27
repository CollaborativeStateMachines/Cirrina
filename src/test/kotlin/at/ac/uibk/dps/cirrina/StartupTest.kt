package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.execution.trace.CausalEvent
import at.ac.uibk.dps.cirrina.util.TestUtils
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class StartupTest {
  @ParameterizedTest
  @CsvSource("startupSender,startupReceiver", "startupReceiver,startupSender")
  fun `startup follows RUN order and delivers initial events in either order`(
    first: String,
    second: String,
  ) {
    val recorder = CollectingCausalTraceRecorder()

    assertTimeoutPreemptively(Duration.ofSeconds(10)) {
      tracedRuntime(TestUtils.resourceUri("pkl/startup/main.pkl"), listOf(first, second), recorder)
        .run()
    }

    val created = recorder.records.filterIsInstance<CausalEvent.InstanceCreated>()

    assertEquals(listOf(first, second), created.map { it.instance })

    val handled = recorder.records.filterIsInstance<CausalEvent.EventHandled>()

    assertEquals(setOf("request", "reply"), handled.map { it.event }.toSet())
    assertEquals(setOf("startupSender", "startupReceiver"), handled.map { it.instance }.toSet())
    handled.forEach { assertEquals("done", it.stateAfter) }

    val emitted = recorder.records.filterIsInstance<CausalEvent.EventEmitted>()

    assertEquals(emitted.map { it.eventId }.toSet(), handled.map { it.eventId }.toSet())
  }
}
