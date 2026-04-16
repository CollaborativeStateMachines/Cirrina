package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml

class Csml private constructor(description: Csml) {
  /** The collaborative machine specification. */
  val collaborativeStateMachine =
    CollaborativeStateMachine.create(this, description.collaborativeStateMachine).getOrThrow()

  /** The list of instance specifications. */
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

  // TODO: This should have a specification
  val bindings = description.bindings

  companion object {
    fun create(description: Csml) = runCatching { Csml(description) }
  }
}
