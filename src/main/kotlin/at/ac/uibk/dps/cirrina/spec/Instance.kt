package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.InstanceDescription

class Instance
private constructor(
  val parent: Csml,
  val stateMachine: StateMachine,
  val name: String,
  description: InstanceDescription,
) {
  val data = description.data

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
