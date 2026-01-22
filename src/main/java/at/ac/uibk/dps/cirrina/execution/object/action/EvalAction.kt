package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

/**
 * An action that evaluates a specific [expression].
 *
 * @property expression the expression to be evaluated.
 */
class EvalAction internal constructor(val expression: Expression) : Action()
