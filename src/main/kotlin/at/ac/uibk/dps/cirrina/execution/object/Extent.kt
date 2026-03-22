package at.ac.uibk.dps.cirrina.execution.`object`

import org.apache.commons.jexl3.JexlContext

class Extent private constructor(private val contexts: Array<Context>) : JexlContext {
  val high: Context? = contexts.lastOrNull()

  fun setOrCreate(name: String, value: Any?) {
    high?.let { if (it.has(name)) it.assign(name, value) else it.create(name, value) }
      ?: error("extent contains no contexts")
  }

  override fun get(name: String): Any = resolve(name)

  override fun set(name: String, value: Any?) {
    contexts.lastOrNull { it.has(name) }?.assign(name, value)
      ?: error("variable '$name' not found in any context")
  }

  fun resolve(name: String): Any =
    contexts.lastOrNull { it.has(name) }?.get(name)
      ?: error("variable '$name' not found in any context")

  override fun has(name: String): Boolean = contexts.any { it.has(name) }

  fun extend(high: Context): Extent = Extent(contexts + high)

  companion object {
    fun empty() = Extent(emptyArray())

    fun of(vararg contexts: Context) = Extent(arrayOf(*contexts))

    fun from(low: List<Context>, high: Context) = Extent((low + high).toTypedArray())
  }
}
