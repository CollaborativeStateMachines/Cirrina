package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable

class ContextVariableExchange(val contextVariable: ContextVariable) {

  companion object {
    fun fromProto(proto: ContextVariableProtos.ContextVariable): ContextVariable =
      ContextVariable.eager(proto.name, ValueExchange.fromProto(proto.value))
  }

  fun toProto(): ContextVariableProtos.ContextVariable =
    ContextVariableProtos.ContextVariable.newBuilder()
      .setName(contextVariable.name)
      .setValue(ValueExchange(contextVariable.value).toProto())
      .build()
}
