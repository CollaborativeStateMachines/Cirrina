package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos

object ContextVariableExchange {
  fun toProto(contextVariable: ContextVariable): ContextVariableProtos.ContextVariable =
    ContextVariableProtos.ContextVariable.newBuilder()
      .setName(contextVariable.name)
      .setValue(ValueExchange.toProto(contextVariable.value))
      .build()

  fun fromProto(proto: ContextVariableProtos.ContextVariable): ContextVariable =
    ContextVariable.eager(proto.name, ValueExchange.fromProto(proto.value))
}
