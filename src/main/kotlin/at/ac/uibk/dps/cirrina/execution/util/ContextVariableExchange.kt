package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos

class ContextVariableExchange(val contextVariable: ContextVariable) {
  fun toProto(): ContextVariableProtos.ContextVariable =
    ContextVariableProtos.ContextVariable.newBuilder()
      .setName(contextVariable.name)
      .setValue(ValueExchange(contextVariable.value).toProto())
      .build()

  companion object {
    fun fromProto(proto: ContextVariableProtos.ContextVariable): ContextVariable =
      ContextVariable.eager(proto.name, ValueExchange.fromProto(proto.value))
  }
}
