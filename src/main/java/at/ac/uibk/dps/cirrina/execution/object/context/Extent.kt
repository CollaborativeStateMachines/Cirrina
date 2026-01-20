package at.ac.uibk.dps.cirrina.execution.`object`.context

/**
 * Represents a hierarchical scope of [Context] objects. Values are resolved from 'high' (most
 * specific) to 'low' (most general).
 */
class Extent(val contexts: List<Context> = emptyList()) {

  companion object {
    fun empty(): Extent = Extent()

    fun of(vararg contexts: Context): Extent = Extent(contexts.toList())
  }

  constructor(low: List<Context>, high: Context) : this(low + high)

  constructor(low: Context, high: Context) : this(listOf(low, high))

  /** Attempts to assign a value to a variable in the 'high' context. */
  fun setOrCreate(name: String, value: Any?): Int =
    high?.let { ctx ->
      if (ctx.has(name)) {
        ctx.assign(name, value)
      } else {
        ctx.create(name, value)
      }
    } ?: error("extent contains no contexts")

  /** Updates [name] in the first context where it exists, searching high to low. */
  fun trySet(name: String, value: Any?): Int {
    for (i in contexts.indices.reversed()) {
      val context = contexts[i]
      if (context.has(name)) {
        return context.assign(name, value)
      }
    }
    error("variable '$name' not found in any context")
  }

  /** Resolves the value of [name] by searching through contexts from high to low. */
  fun resolve(name: String): Any {
    for (i in contexts.indices.reversed()) {
      val context = contexts[i]

      if (context.has(name)) {
        return context.get(name) ?: Unit
      }
    }
    error("variable '$name' not found in any context")
  }

  fun extend(high: Context): Extent = Extent(this.contexts, high)

  val high: Context?
    get() = contexts.lastOrNull()
}
