package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.InstanceDescription

class Instance private constructor(csml: Csml, val name: String, description: InstanceDescription) {
  /** The specification of the state machine to instantiate. */
  val stateMachine: StateMachine by lazy {
    csml.collaborativeStateMachine.getStateMachine(description.stateMachineName)
  }

  /** The list of instance context data variables. */
  val data: Map<String, String>? = description.data

  /** The subscription regular expression */
  val subscription: Regex = description.subscription.toRegex()

  companion object {
    fun create(csml: Csml, description: InstanceDescription, name: String) = runCatching {
      Instance(csml, name, description)
    }
  }
}
