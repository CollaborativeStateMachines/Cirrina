package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable

class ContextVariableExchange(val contextVariable: ContextVariable) {

  companion object {
    fun fromProto(proto: ContextVariableProtos.ContextVariable): Result<ContextVariable> =
      runCatching {
        ContextVariable.eager(proto.name, ValueExchange.fromProto(proto.value).getOrThrow())
      }
  }

  fun toProto(): Result<ContextVariableProtos.ContextVariable> = runCatching {
    ContextVariableProtos.ContextVariable.newBuilder()
      .setName(contextVariable.name)
      .setValue(ValueExchange(contextVariable.value).toProto().getOrThrow())
      .build()
  }
}
