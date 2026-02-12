package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.Instance as InstanceDescription

class Instance
private constructor(
  val name: String,
  val stateMachine: StateMachine,
  val data: Map<String, String>?,
  val subscriptions: List<String>?,
) {
  fun isSubscribedTo(other: String): Boolean = subscriptions?.contains(other) == true

  companion object {
    fun create(description: InstanceDescription, stateMachine: StateMachine, name: String) =
      Instance(name, stateMachine, description.data, description.subscriptions)
  }
}
