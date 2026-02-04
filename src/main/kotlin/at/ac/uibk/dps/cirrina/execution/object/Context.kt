package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.execution.provider.InMemoryContext

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

interface Context : AutoCloseable {
  fun has(name: String): Boolean

  fun get(name: String): Any?

  fun create(name: String, value: Any?): Int

  fun assign(name: String, value: Any?): Int

  fun delete(name: String)

  fun deleteAll()

  fun getAll(): List<ContextVariable>

  companion object {
    fun from(description: Map<String, String>?): Result<Context> = runCatching {
      val ctx = InMemoryContext()
      description?.forEach { (name, expr) ->
        val expression = Expression.from(expr).getOrThrow()
        val value = expression.execute(Extent.empty())
        ctx.create(name, value)
      }
      ctx
    }
  }
}
