package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.csm.Csml.InvocationMode
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

/**
 * An action that invokes a specific service type.
 *
 * @property type the identifier of the service to be invoked.
 * @property mode the [InvocationMode] for the service call.
 * @property input the list of context variables passed as input to the service.
 * @property raises the list of events to be raised upon successful service completion.
 */
class InvokeAction(
  val type: String,
  val mode: InvocationMode,
  val input: List<ContextVariable>,
  val raises: List<Event>,
) : Action(), EventRaisingAction {

  /**
   * Returns the list of [Event]s to be triggered by this action.
   *
   * @return the list of events associated with the completion of this invocation.
   */
  override fun raises(): List<Event> = raises
}
