package at.ac.uibk.dps.cirrina.execution.`object`.guard

import at.ac.uibk.dps.cirrina.execution.`object`.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.Guard
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
      assertDoesNotThrow { Guard.from("v==5").evaluate(extent) }

      // Success case
      assertDoesNotThrow { Guard.from("v==6").evaluate(extent) }

      // Error case
      assertThrows<IllegalArgumentException> { Guard.from("v").evaluate(extent) }
    }
  }
}
