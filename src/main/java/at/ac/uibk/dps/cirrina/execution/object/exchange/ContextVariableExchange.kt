package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable

class ContextVariableExchange(val contextVariable: ContextVariable) {

  companion object {
    @JvmStatic
    fun fromBytes(data: ByteArray): Result<ContextVariableExchange> =
      runCatching {
          val proto = ContextVariableProtos.ContextVariable.parseFrom(data)
          fromProto(proto).getOrThrow()
        }
        .map { ContextVariableExchange(it) }
        .recoverCatching { ex ->
          throw UnsupportedOperationException("could not read context variable from bytes", ex)
        }

    @JvmStatic
    fun fromProto(proto: ContextVariableProtos.ContextVariable): Result<ContextVariable> =
      runCatching {
        ContextVariable.eager(proto.name, ValueExchange.fromProto(proto.value).getOrThrow())
      }
  }

  fun toBytes(): Result<ByteArray> = toProto().map { it.toByteArray() }

  fun toProto(): Result<ContextVariableProtos.ContextVariable> = runCatching {
    ContextVariableProtos.ContextVariable.newBuilder()
      .setName(contextVariable.name)
      .setValue(ValueExchange(contextVariable.value).toProto().getOrThrow())
      .build()
  }
}
