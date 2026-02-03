package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.csm.Csml.InvocationMode
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

class InvokeAction
internal constructor(
  val type: String,
  val mode: InvocationMode,
  val input: List<ContextVariable>,
  val raises: List<Event>,
) : EventRaisingAction {

  override fun raises(): List<Event> = raises
}
