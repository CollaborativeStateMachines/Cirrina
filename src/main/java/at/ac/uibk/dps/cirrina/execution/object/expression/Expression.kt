package at.ac.uibk.dps.cirrina.execution.`object`.expression

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent

abstract class Expression(val source: String) {

  abstract fun execute(extent: Extent): Result<Any?>

  override fun toString(): String = source
}
