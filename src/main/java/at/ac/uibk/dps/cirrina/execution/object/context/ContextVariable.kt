package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

data class ContextVariable(val name: String, val value: Any?, val isLazy: Boolean = false) {

  companion object {
    fun lazy(name: String, expression: Expression): ContextVariable =
      ContextVariable(name, expression, isLazy = true)

    fun eager(name: String, value: Any?): ContextVariable =
      ContextVariable(name, value, isLazy = false)
  }

  init {
    require(name.isNotBlank()) { "name cannot be null or blank" }
  }

  fun evaluate(extent: Extent): Result<ContextVariable> {
    if (!isLazy) return Result.success(this)

    return runCatching {
        val expression =
          value as? Expression
            ?: throw IllegalStateException(
              "variable '$name' is marked lazy but value is not an Expression"
            )

        val evaluatedValue = expression.execute(extent).getOrThrow()
        copy(value = evaluatedValue, isLazy = false)
      }
      .recoverCatching { ex ->
        throw UnsupportedOperationException("could not evaluate variable '$name'", ex)
      }
  }

  override fun toString(): String = "{$name = $value}"
}
