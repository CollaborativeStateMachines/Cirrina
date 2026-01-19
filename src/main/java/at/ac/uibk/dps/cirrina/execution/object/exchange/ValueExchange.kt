package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos.Value.ValueCase
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos.ValueCollection
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos.ValueMap
import com.google.protobuf.ByteString

class ValueExchange(val value: Any?) {

  companion object {
    @JvmStatic
    fun fromBytes(data: ByteArray): Result<ValueExchange> =
      runCatching {
          val proto = ContextVariableProtos.Value.parseFrom(data)
          fromProto(proto).getOrThrow()
        }
        .map { ValueExchange(it) }
        .recoverCatching { ex ->
          throw UnsupportedOperationException("could not read value from bytes", ex)
        }

    @JvmStatic
    fun fromProto(proto: ContextVariableProtos.Value): Result<Any?> = runCatching {
      when (proto.valueCase) {
        ValueCase.INTEGER -> proto.integer
        ValueCase.FLOAT -> proto.float
        ValueCase.LONG -> proto.long
        ValueCase.DOUBLE -> proto.double
        ValueCase.STRING -> proto.string
        ValueCase.BOOL -> proto.bool
        ValueCase.BYTES -> proto.bytes.toByteArray()
        ValueCase.ARRAY -> fromCollectionProto(proto.array).getOrThrow().toTypedArray()
        ValueCase.LIST -> fromCollectionProto(proto.list).getOrThrow().toMutableList()
        ValueCase.MAP -> fromMapProto(proto.map).getOrThrow()
        ValueCase.VALUE_NOT_SET,
        null -> null
        else -> throw UnsupportedOperationException("context variable value type could not be read")
      }
    }

    private fun fromCollectionProto(collection: ValueCollection): Result<List<Any?>> = runCatching {
      collection.entryList.map { fromProto(it).getOrThrow() }
    }

    private fun fromMapProto(map: ValueMap): Result<Map<Any?, Any?>> = runCatching {
      map.entryList.associate { entry ->
        fromProto(entry.key).getOrThrow() to fromProto(entry.value).getOrThrow()
      }
    }

    private fun toCollectionProto(list: Iterable<*>): Result<ValueCollection> = runCatching {
      ValueCollection.newBuilder()
        .addAllEntry(list.map { ValueExchange(it).toProto().getOrThrow() })
        .build()
    }

    private fun toMapProto(map: Map<*, *>): Result<ValueMap> = runCatching {
      ValueMap.newBuilder()
        .addAllEntry(
          map.entries.map { (key, value) ->
            ContextVariableProtos.ValueMapEntry.newBuilder()
              .setKey(ValueExchange(key).toProto().getOrThrow())
              .setValue(ValueExchange(value).toProto().getOrThrow())
              .build()
          }
        )
        .build()
    }
  }

  fun toBytes(): Result<ByteArray> = toProto().map { it.toByteArray() }

  fun toProto(): Result<ContextVariableProtos.Value> = runCatching {
    val builder = ContextVariableProtos.Value.newBuilder()

    when (value) {
      is Int -> builder.integer = value
      is Float -> builder.float = value
      is Long -> builder.long = value
      is Double -> builder.double = value
      is String -> builder.string = value
      is Boolean -> builder.bool = value
      is ByteArray -> builder.bytes = ByteString.copyFrom(value)
      is Array<*> -> builder.array = toCollectionProto(value.toList()).getOrThrow()
      is List<*> -> builder.list = toCollectionProto(value).getOrThrow()
      is Map<*, *> -> builder.map = toMapProto(value).getOrThrow()
      null -> builder.clear()
      else -> throw UnsupportedOperationException("value type could not be converted to proto")
    }

    builder.build()
  }
}
