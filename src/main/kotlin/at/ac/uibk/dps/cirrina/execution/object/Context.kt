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
    fun empty() = ContextInMemory()
  }
}
