package at.ac.uibk.dps.cirrina.execution.`object`.action

/**
 * An action that cancels or stops a previously scheduled [TimeoutAction].
 *
 * @property action the name of the timeout action to be reset.
 */
class TimeoutResetAction(val action: String) : Action()
