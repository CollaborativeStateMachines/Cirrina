package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.execution.provider.JexlExpression
import java.util.concurrent.ConcurrentHashMap

abstract class Expression(val source: String) {
  abstract fun execute(extent: Extent): Any?

  override fun toString(): String = "${this::class.simpleName}(source='$source')"

  companion object {
    private val cache = ConcurrentHashMap<String, Expression>()

    fun from(source: String): Expression = cache.computeIfAbsent(source) { JexlExpression(it) }
  }
}
