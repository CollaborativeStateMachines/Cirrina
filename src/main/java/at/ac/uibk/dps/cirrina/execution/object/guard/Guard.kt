package at.ac.uibk.dps.cirrina.execution.`object`.guard

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

class Guard internal constructor(val expression: Expression) {

  fun evaluate(extent: Extent): Result<Boolean> =
    expression.execute(extent).mapCatching { result ->
      require(result is Boolean) {
        "guard expression '$expression' does not produce a boolean value"
      }
      result
    }
}
