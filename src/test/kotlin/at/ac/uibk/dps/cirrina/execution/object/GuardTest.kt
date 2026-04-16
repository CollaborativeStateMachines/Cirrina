package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class GuardTest {
  @Test
  fun testGuard() {
    ContextInMemory().use { context ->
      context.create("v", 5)
      val extent = Extent.of(context)

      // Success case
      assertDoesNotThrow { "v==5".evaluatesToTrue(extent) }

      // Success case
      assertDoesNotThrow { "v==6".evaluatesToTrue(extent) }

      // Error case
      assertThrows<IllegalArgumentException> { "v".evaluatesToTrue(extent) }
    }
  }
}
