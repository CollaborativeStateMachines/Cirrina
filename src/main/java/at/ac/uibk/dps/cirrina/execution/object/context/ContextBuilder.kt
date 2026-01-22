package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.expression.ExpressionBuilder

class ContextBuilder
private constructor(private val contextDescription: Map<String, String>? = null) {
  private var context: Context? = null

  companion object {
    fun empty(): ContextBuilder = ContextBuilder()

    fun from(contextDescription: Map<String, String>?): ContextBuilder =
      ContextBuilder(contextDescription)
  }

  fun inMemoryContext(): ContextBuilder = apply { context = InMemoryContext() }

  fun build(): Result<Context> {
    val currentContext =
      context
        ?: return Result.failure(
          IllegalStateException("context implementation must be specified before building.")
        )

    val description = contextDescription ?: return Result.success(currentContext)

    return description.entries.fold(Result.success(currentContext)) { acc, (name, expr) ->
      acc.mapCatching { ctx ->
        val expression = ExpressionBuilder.from(expr).build().getOrThrow()
        val value = expression.execute(Extent.empty())

        ctx.create(name, value)

        ctx
      }
    }
  }
}
