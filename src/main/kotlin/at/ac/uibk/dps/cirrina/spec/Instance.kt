package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.InstanceDescription
import at.ac.uibk.dps.cirrina.execution.`object`.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.evaluate

class Instance private constructor(val name: String, csml: Csml, description: InstanceDescription) {
  /** The specification of the state machine to instantiate. */
  val stateMachine: StateMachine by lazy {
    csml.collaborativeStateMachine.getStateMachine(description.stateMachineName)
  }

  /** The list of instance context data variables. */
  val data: Map<String, Expression> = description.data.mapValues { (_, v) -> Expression(v) }

  /** The subscription regular expression */
  val subscription: Regex = description.subscription.toRegex()

  companion object {
    fun create(csml: Csml, description: InstanceDescription, name: String) = runCatching {
      Instance(name, csml, description)
    }
  }
}

class DynamicInstance
private constructor(val name: Expression, val csml: Csml, val description: InstanceDescription) {
  fun evaluate(extent: Extent) = Instance.create(csml, description, name.evaluate(extent) as String)

  companion object {
    fun create(csml: Csml, description: InstanceDescription, name: Expression) = runCatching {
      DynamicInstance(name, csml, description)
    }
  }
}
