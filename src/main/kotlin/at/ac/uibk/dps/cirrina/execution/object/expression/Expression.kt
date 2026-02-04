package at.ac.uibk.dps.cirrina.execution.`object`.expression

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import java.util.concurrent.ConcurrentHashMap

abstract class Expression(val source: String) {

  abstract fun execute(extent: Extent): Any?

  override fun toString(): String = "${this::class.simpleName}(source='$source')"

  companion object {
    private val cache = ConcurrentHashMap<String, Expression>()

    fun from(source: String): Result<Expression> = runCatching {
      require(source.isNotBlank()) { "expression source cannot be blank" }
      cache.computeIfAbsent(source) { JexlExpression(it) }
    }
  }
}
