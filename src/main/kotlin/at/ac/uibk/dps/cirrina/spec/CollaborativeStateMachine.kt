package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.CollaborativeStateMachineDescription
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable

class CollaborativeStateMachine
private constructor(
  val persistentContext: List<ContextVariable>,
  val stateMachines: Map<String, StateMachine>,
) {
  companion object {
    fun create(
      description: CollaborativeStateMachineDescription
    ): Result<CollaborativeStateMachine> = runCatching {
      val variables = Context.from(description.persistent).getAll()

      val stateMachines =
        description.stateMachines.mapValues { (name, desc) ->
          StateMachine.create(desc, name).getOrThrow()
        }

      CollaborativeStateMachine(variables, stateMachines)
    }
  }
}
