package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

class TimeoutAction
internal constructor(val name: String, val delay: Expression, val `do`: Action) :
  EventRaisingAction {

  override fun raises(): List<Event> =
    (`do` as? RaiseAction)?.let { listOf(it.event) } ?: emptyList()
}
