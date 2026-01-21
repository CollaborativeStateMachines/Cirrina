package at.ac.uibk.dps.cirrina.execution.`object`.context

class Extent private constructor(private val contexts: Array<Context>) {

  private val size: Int = contexts.size

  val high: Context? = if (size > 0) contexts[size - 1] else null

  companion object {
    private val EMPTY_ARRAY = emptyArray<Context>()

    fun empty(): Extent = Extent(EMPTY_ARRAY)

    fun of(vararg contexts: Context): Extent = Extent(Array(contexts.size) { contexts[it] })
  }

  constructor(
    low: List<Context>,
    high: Context,
  ) : this(Array(low.size + 1) { i -> if (i < low.size) low[i] else high })

  constructor(low: Context, high: Context) : this(arrayOf(low, high))

  fun setOrCreate(name: String, value: Any?): Int =
    high?.let { ctx -> if (ctx.has(name)) ctx.assign(name, value) else ctx.create(name, value) }
      ?: error("extent contains no contexts")

  fun set(name: String, value: Any?): Int {
    var i = size - 1
    while (i >= 0) {
      val ctx = contexts[i]
      if (ctx.has(name)) {
        return ctx.assign(name, value)
      }
      --i
    }
    error("variable '$name' not found in any context")
  }

  fun resolve(name: String): Any {
    var i = size - 1
    while (i >= 0) {
      val ctx = contexts[i]
      if (ctx.has(name)) {
        return ctx.get(name) ?: Unit
      }
      --i
    }
    error("variable '$name' not found in any context")
  }

  fun extend(high: Context): Extent {
    val newArray = Array(size + 1) { i -> if (i < size) contexts[i] else high }
    return Extent(newArray)
  }
}
