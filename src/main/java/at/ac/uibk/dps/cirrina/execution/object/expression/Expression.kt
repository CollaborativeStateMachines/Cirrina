package at.ac.uibk.dps.cirrina.execution.`object`.expression

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import org.apache.commons.lang3.builder.ToStringBuilder

abstract class Expression(val source: String) {

  abstract fun execute(extent: Extent): Any?

  override fun toString(): String = ToStringBuilder(this).append("source", source).toString()
}
