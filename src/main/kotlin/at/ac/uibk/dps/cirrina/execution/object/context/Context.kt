package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

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
