package at.ac.uibk.dps.cirrina.execution.`object`

class Extent private constructor(private val contexts: Array<Context>) {
  val high: Context? = contexts.lastOrNull()

  fun setOrCreate(name: String, value: Any?): Int =
    high?.let { if (it.has(name)) it.assign(name, value) else it.create(name, value) }
      ?: error("extent contains no contexts")

  fun set(name: String, value: Any?): Int =
    contexts.lastOrNull { it.has(name) }?.assign(name, value)
      ?: error("variable '$name' not found in any context")

  fun resolve(name: String): Any =
    contexts.lastOrNull { it.has(name) }?.get(name)
      ?: error("variable '$name' not found in any context")

  fun has(name: String): Boolean = contexts.any { it.has(name) }

  fun extend(high: Context): Extent = Extent(contexts + high)

  companion object {
    fun empty() = Extent(emptyArray())

    fun of(vararg contexts: Context) = Extent(arrayOf(*contexts))

    fun from(low: List<Context>, high: Context) = Extent((low + high).toTypedArray())
  }
}
