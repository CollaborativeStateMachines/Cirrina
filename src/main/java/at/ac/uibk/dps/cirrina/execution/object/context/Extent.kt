package at.ac.uibk.dps.cirrina.execution.`object`.context

class Extent private constructor(private val contexts: Array<Context>) {

  private val size: Int = contexts.size

  val high: Context? = contexts.lastOrNull()

  companion object {
    fun empty(): Extent = Extent(emptyArray())

    fun of(vararg contexts: Context): Extent = Extent(Array(contexts.size) { contexts[it] })
  }

  constructor(
    low: List<Context>,
    high: Context,
  ) : this(Array(low.size + 1) { i -> if (i < low.size) low[i] else high })

  constructor(low: Context, high: Context) : this(arrayOf(low, high))

  fun setOrCreate(name: String, value: Any?): Int =
    high?.run { if (has(name)) assign(name, value) else create(name, value) }
      ?: error("Extent contains no contexts")

  fun set(name: String, value: Any?): Int =
    contexts.findLast { it.has(name) }?.assign(name, value)
      ?: error("Variable '$name' not found in any context")

  fun resolve(name: String): Any =
    contexts.findLast { it.has(name) }?.let { it.get(name) ?: Unit }
      ?: error("Variable '$name' not found in any context")

  fun has(name: String): Boolean = contexts.any { it.has(name) }

  fun extend(high: Context): Extent = Extent(contexts + high)
}
