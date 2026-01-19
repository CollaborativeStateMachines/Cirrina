package at.ac.uibk.dps.cirrina.execution.`object`.guard

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import at.ac.uibk.dps.cirrina.utils.assertFailure
import at.ac.uibk.dps.cirrina.utils.assertSuccess
import org.junit.jupiter.api.Test

class GuardTest {

  @Test
  fun testGuard() {
    InMemoryContext(true).use { context ->
      context.create("v", 5)
      val extent = Extent.of(context)

      GuardBuilder.from("v==5").build().onSuccess { guard ->
        guard.evaluate(extent).assertSuccess()
      }

      GuardBuilder.from("v==6").build().onSuccess { guard ->
        guard.evaluate(extent).assertSuccess()
      }

      GuardBuilder.from("v").build().onSuccess { guard -> guard.evaluate(extent).assertFailure() }
    }
  }
}
