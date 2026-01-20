package at.ac.uibk.dps.cirrina.execution.`object`.guard

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

class Guard internal constructor(val expression: Expression) {

  fun evaluate(extent: Extent): Boolean {
    val result = expression.execute(extent).getOrThrow()

    require(result is Boolean) {
      "guard expression '$expression' does not produce a boolean value (produced: ${result?.javaClass?.simpleName})"
    }

    return result
  }
}
