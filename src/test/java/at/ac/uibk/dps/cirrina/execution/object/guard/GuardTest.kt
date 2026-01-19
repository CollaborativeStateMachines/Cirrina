package at.ac.uibk.dps.cirrina.execution.`object`.guard

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuardTest {

  @Test
  fun testGuard() {
    InMemoryContext(true).use { context ->
      context.create("v", 5)
      val extent = Extent.of(context)

      GuardBuilder.from("v==5").build().onSuccess { guard ->
        guard.evaluate(extent).onSuccess { result -> assertTrue(result) }
      }

      GuardBuilder.from("v==6").build().onSuccess { guard ->
        guard.evaluate(extent).onSuccess { result -> assertFalse(result) }
      }

      val guardResult = GuardBuilder.from("v").build()
      assertTrue(guardResult.isFailure)
    }
  }
}
