package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

class ContextVariableBuilder private constructor() {

  companion object {
    @JvmStatic fun empty(): ContextVariableBuilder = ContextVariableBuilder()
  }

  private var name: String? = null
  private var variableInitializer: Initializer? = null

  private sealed interface Initializer {
    data class Value(val data: Any?) : Initializer

    data class Expr(val expression: Expression) : Initializer
  }

  fun name(name: String): ContextVariableBuilder = apply { this.name = name }

  fun value(value: Any?): ContextVariableBuilder = apply {
    this.variableInitializer = Initializer.Value(value)
  }

  fun expression(expression: Expression): ContextVariableBuilder = apply {
    this.variableInitializer = Initializer.Expr(expression)
  }

  fun build(): Result<ContextVariable> {
    val currentName =
      name ?: return Result.failure(IllegalStateException("variable name must be provided"))

    return when (val init = variableInitializer) {
      is Initializer.Value -> Result.success(ContextVariable.eager(currentName, init.data))
      is Initializer.Expr -> Result.success(ContextVariable.lazy(currentName, init.expression))
      null ->
        Result.failure(
          IllegalStateException(
            "either a value or an expression must be provided for variable '$currentName'"
          )
        )
    }
  }
}
