package at.ac.uibk.dps.cirrina.execution.`object`.expression

import java.util.concurrent.ConcurrentHashMap

class ExpressionBuilder private constructor(private val source: String) {

  companion object {
    private val cache = ConcurrentHashMap<String, Expression>()

    fun from(source: String): ExpressionBuilder = ExpressionBuilder(source)
  }

  fun build(): Result<Expression> {
    if (source.isBlank()) {
      return Result.failure(IllegalArgumentException("expression source cannot be blank"))
    }

    return runCatching { cache.computeIfAbsent(source) { JexlExpression(it) } }
  }
}
