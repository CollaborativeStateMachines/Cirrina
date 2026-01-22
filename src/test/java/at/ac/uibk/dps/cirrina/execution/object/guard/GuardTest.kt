package at.ac.uibk.dps.cirrina.execution.`object`.guard

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class GuardTest {

  @Test
  fun testGuard() {
    InMemoryContext(true).use { context ->
      context.create("v", 5)
      val extent = Extent.of(context)

      // Success case
      assertDoesNotThrow {
        GuardBuilder.from("v==5").build().onSuccess { guard -> guard.evaluate(extent) }
      }

      // Success case
      assertDoesNotThrow {
        GuardBuilder.from("v==6").build().onSuccess { guard -> guard.evaluate(extent) }
      }

      // Error case
      assertThrows<IllegalArgumentException> {
        GuardBuilder.from("v").build().onSuccess { guard -> guard.evaluate(extent) }
      }
    }
  }
}
