package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.CollaborativeStateMachineDescription

class CollaborativeStateMachine
private constructor(csml: Csml, description: CollaborativeStateMachineDescription) {
  /** The list of persistent context variables. */
  val persistentContext: Map<String, Expression> =
    description.persistent.mapValues { (_, v) -> Expression(v) }

  /** The map of state machine specifications. */
  val stateMachines =
    description.stateMachines.mapValues { (name, desc) ->
      StateMachine.create(csml, desc, name).getOrThrow()
    }

  fun getStateMachine(name: String) =
    stateMachines[name] ?: error("state machine class '${name}' not found")

  companion object {
    fun create(csml: Csml, description: CollaborativeStateMachineDescription) = runCatching {
      CollaborativeStateMachine(csml, description)
    }
  }
}
