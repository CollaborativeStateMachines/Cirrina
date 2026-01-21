package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

/**
 * An action that evaluates a [value] expression and executes a matching branch from [cases].
 *
 * @property value the expression to be matched.
 * @property cases a mapping of expressions to their corresponding actions.
 * @property default the fallback action to execute if no cases match.
 */
class MatchAction(
  val value: Expression,
  val cases: Map<Expression, Action>,
  val default: Action? = null,
) : Action(), EventRaisingAction {

  /**
   * Returns the list of [Event]s to be triggered by this action.
   *
   * @return the list of events raised within the matching branches or default.
   */
  override fun raises(): List<Event> =
    (cases.values + listOfNotNull(default)).filterIsInstance<EventRaisingAction>().flatMap {
      it.raises()
    }
}
