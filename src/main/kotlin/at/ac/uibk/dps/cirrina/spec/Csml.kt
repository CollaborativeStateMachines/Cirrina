package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml

class Csml
private constructor(
  val collaborativeStateMachine: CollaborativeStateMachine,
  val instances: List<Instance>,
  val bindings: List<Csml.ServiceImplementationBinding>?,
) {
  companion object {
    fun create(description: Csml): Result<at.ac.uibk.dps.cirrina.spec.Csml> = runCatching {
      val spec =
        CollaborativeStateMachine.create(description.collaborativeStateMachine).getOrThrow()

      val instances =
        description.instances.map { (name, desc) ->
          Instance.create(
            desc,
            spec.stateMachines[desc.stateMachineName]
              ?: error("state machine class '${desc.stateMachineName}' not found"),
            name,
          )
        }

      Csml(spec, instances, description.bindings)
    }
  }
}
