package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

data class ContextVariable(val name: String, val value: Any?, val isLazy: Boolean = false) {

  init {
    require(name.isNotBlank()) { "name cannot be blank" }
  }

  fun evaluate(extent: Extent): ContextVariable =
    if (isLazy) {
      val expression =
        value as? Expression
          ?: error("variable '$name' is marked lazy but value is not an expression")
      copy(value = expression.execute(extent), isLazy = false)
    } else this

  companion object {
    fun lazy(name: String, expression: Expression) = ContextVariable(name, expression, true)

    fun eager(name: String, value: Any?) = ContextVariable(name, value, false)
  }
}
