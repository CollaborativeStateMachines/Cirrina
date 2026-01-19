package at.ac.uibk.dps.cirrina.execution.`object`.guard

import at.ac.uibk.dps.cirrina.execution.`object`.expression.ExpressionBuilder

class GuardBuilder private constructor(private val expressionDescription: String) {

  companion object {
    @JvmStatic
    fun from(expressionDescription: String): GuardBuilder = GuardBuilder(expressionDescription)
  }

  fun build(): Result<Guard> =
    ExpressionBuilder.from(expressionDescription).build().map { Guard(it) }
}
