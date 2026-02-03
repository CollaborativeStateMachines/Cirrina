package at.ac.uibk.dps.cirrina.execution.`object`.guard

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

class Guard internal constructor(val expression: Expression) {

  fun evaluate(extent: Extent): Boolean {
    val result = expression.execute(extent)
    require(result is Boolean) { "guard expression '$expression' did not produce a boolean" }
    return result
  }

  companion object {
    fun from(expressionDescription: String): Result<Guard> =
      Expression.from(expressionDescription).map(::Guard)
  }
}
