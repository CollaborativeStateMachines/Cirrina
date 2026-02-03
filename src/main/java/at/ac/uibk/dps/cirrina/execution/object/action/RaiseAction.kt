package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

class RaiseAction internal constructor(val event: Event, val target: Expression?) :
  EventRaisingAction {

  override fun raises(): List<Event> = listOf(event)
}
