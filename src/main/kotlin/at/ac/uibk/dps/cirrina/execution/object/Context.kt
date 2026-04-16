package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.spec.ContextVariable
import at.ac.uibk.dps.cirrina.spec.LazyContextVariable

fun LazyContextVariable.evaluate(extent: Extent) =
  ContextVariable(name, expression.evaluate(extent))

interface Context : AutoCloseable {
  fun has(name: String): Boolean

  fun get(name: String): Any?

  fun create(name: String, value: Any?): Int

  fun assign(name: String, value: Any?): Int

  fun delete(name: String)

  fun deleteAll()

  fun getAll(): List<ContextVariable>

  fun clear()

  companion object {
    fun from(description: Map<String, String>?): Context {
      val ctx = ContextInMemory()
      description?.forEach { (name, expr) ->
        val value = expr.evaluate(Extent.empty())
        ctx.create(name, value)
      }
      return ctx
    }

    fun empty() = ContextInMemory()
  }
}
