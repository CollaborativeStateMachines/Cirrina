package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable

class Csml
private constructor(
  val collaborativeStateMachineSpec: CollaborativeStateMachine,
  val instances: Map<String, String>,
  val instanceSubscriptions: Map<String, List<String>>,
  val instanceData: Map<String, List<ContextVariable>>,
) {

  companion object {
    fun create(desc: Csml): Result<at.ac.uibk.dps.cirrina.spec.Csml> = runCatching {
      val spec = CollaborativeStateMachine.create(desc.collaborativeStateMachine).getOrThrow()

      val instanceData =
        desc.instanceData.mapValues { (_, innerMap) ->
          ContextBuilder.from(innerMap).inMemoryContext().build().getOrThrow().getAll()
        }

      Csml(spec, desc.instances, desc.instanceSubscriptions, instanceData)
    }
  }
}
