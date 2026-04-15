package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml

class Csml private constructor(description: Csml) {
  val collaborativeStateMachine =
    CollaborativeStateMachine.create(this, description.collaborativeStateMachine).getOrThrow()

  val instances =
    description.instances.map { (name, desc) ->
      Instance.create(
          this,
          desc,
          collaborativeStateMachine.getStateMachine(desc.stateMachineName),
          name,
        )
        .getOrThrow()
    }

  val bindings = description.bindings

  companion object {
    fun create(description: Csml) = runCatching { Csml(description) }
  }
}
