package at.ac.uibk.dps.cirrina.execution.`object`.context

class Extent(val contexts: List<Context> = emptyList()) {

  companion object {
    @JvmStatic fun empty(): Extent = Extent()

    @JvmStatic fun of(vararg contexts: Context): Extent = Extent(contexts.toList())
  }

  constructor(low: List<Context>, high: Context) : this(low + high)

  constructor(low: Context, high: Context) : this(listOf(low, high))

  fun setOrCreate(name: String, value: Any?): Result<Int> {
    val last = high ?: return Result.failure(IllegalStateException("extent contains no contexts"))

    return last.assign(name, value).recoverCatching { last.create(name, value).getOrThrow() }
  }

  fun trySet(name: String, value: Any?): Result<SetResult> {
    if (contexts.isEmpty()) {
      return Result.failure(IllegalStateException("no contexts found to assign to"))
    }

    for (context in contexts.reversed()) {
      val result = context.assign(name, value)
      if (result.isSuccess) {
        return Result.success(SetResult(result.getOrThrow(), context))
      }
    }

    return Result.failure(
      NoSuchElementException("variable '$name' could not be found in any context in the extent")
    )
  }

  fun extend(high: Context): Extent = Extent(this.contexts, high)

  val low: Context?
    get() = contexts.firstOrNull()

  val high: Context?
    get() = contexts.lastOrNull()

  fun resolve(name: String): Any? =
    contexts
      .asReversed()
      .asSequence()
      .map { it.get(name) }
      .firstOrNull { it.isSuccess }
      ?.getOrNull()

  data class SetResult(val size: Int, val context: Context)
}
