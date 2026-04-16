package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.InstanceDescription
import at.ac.uibk.dps.cirrina.execution.`object`.Context

class Instance
private constructor(
  val parent: Csml,
  val stateMachine: StateMachine,
  val name: String,
  description: InstanceDescription,
) {
  /** The list of instance context data variables. */
  val data = Context.from(description.data).getAll()

  /** The subscription regular expression */
  val subscription: Regex = description.subscription.toRegex()

  companion object {
    fun create(
      parent: Csml,
      description: InstanceDescription,
      stateMachine: StateMachine,
      name: String,
    ) = runCatching { Instance(parent, stateMachine, name, description) }
  }
}
