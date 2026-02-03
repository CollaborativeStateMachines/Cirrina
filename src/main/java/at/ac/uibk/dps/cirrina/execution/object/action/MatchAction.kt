package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

class MatchAction
internal constructor(
  val value: Expression,
  val cases: Map<Expression, Action>,
  val default: Action? = null,
) : EventRaisingAction {

  override fun raises(): List<Event> =
    (cases.values + listOfNotNull(default)).filterIsInstance<EventRaisingAction>().flatMap {
      it.raises()
    }
}
