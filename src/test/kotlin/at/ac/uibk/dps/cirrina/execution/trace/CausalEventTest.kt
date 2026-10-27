package at.ac.uibk.dps.cirrina.execution.trace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CausalEventTest {
  @Test
  fun `copying a handling record preserves its causal identity`() {
    val handling =
      CausalEvent.EventHandled("i", "m", "e", "event-id", "INTERNAL", "a", null, emptyList())
    val completed = handling.copy(stateAfter = "b", emittedEventIds = listOf("next-event"))

    assertEquals(handling.id, completed.id)
    assertEquals(handling.timestamp, completed.timestamp)
  }
}
