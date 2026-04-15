package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.CollaborativeStateMachineDescription
import at.ac.uibk.dps.cirrina.execution.`object`.Context

class CollaborativeStateMachine
private constructor(val parent: Csml, description: CollaborativeStateMachineDescription) {
  val persistentContext = Context.from(description.persistent).getAll()

  val stateMachines =
    description.stateMachines.mapValues { (name, desc) ->
      StateMachine.create(this, desc, name).getOrThrow()
    }

  fun getStateMachine(name: String) =
    stateMachines[name] ?: error("state machine class '${name}' not found")

  companion object {
    fun create(parent: Csml, description: CollaborativeStateMachineDescription) = runCatching {
      CollaborativeStateMachine(parent, description)
    }
  }
}
