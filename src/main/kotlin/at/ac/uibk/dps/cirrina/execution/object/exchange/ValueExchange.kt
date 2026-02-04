package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos.Value.ValueCase
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos.ValueCollection
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos.ValueMap
import com.google.protobuf.ByteString

class ValueExchange(val value: Any?) {

  companion object {
    fun fromBytes(data: ByteArray): ValueExchange =
      ValueExchange(fromProto(ContextVariableProtos.Value.parseFrom(data)))

    fun fromProto(proto: ContextVariableProtos.Value): Any? =
      when (proto.valueCase) {
        ValueCase.INTEGER -> proto.integer
        ValueCase.FLOAT -> proto.float
        ValueCase.LONG -> proto.long
        ValueCase.DOUBLE -> proto.double
        ValueCase.STRING -> proto.string
        ValueCase.BOOL -> proto.bool
        ValueCase.BYTES -> proto.bytes.toByteArray()
        ValueCase.ARRAY -> fromCollectionProto(proto.array).toTypedArray()
        ValueCase.LIST -> fromCollectionProto(proto.list).toMutableList()
        ValueCase.MAP -> fromMapProto(proto.map)
        ValueCase.VALUE_NOT_SET,
        null -> null
      }

    private fun fromCollectionProto(collection: ValueCollection): List<Any?> =
      collection.entryList.map { fromProto(it) }

    private fun fromMapProto(map: ValueMap): Map<Any?, Any?> =
      map.entryList.associate { entry -> fromProto(entry.key) to fromProto(entry.value) }

    private fun toCollectionProto(list: Iterable<*>): ValueCollection =
      ValueCollection.newBuilder().addAllEntry(list.map { ValueExchange(it).toProto() }).build()

    private fun toMapProto(map: Map<*, *>): ValueMap =
      ValueMap.newBuilder()
        .addAllEntry(
          map.entries.map { (key, value) ->
            ContextVariableProtos.ValueMapEntry.newBuilder()
              .setKey(ValueExchange(key).toProto())
              .setValue(ValueExchange(value).toProto())
              .build()
          }
        )
        .build()
  }

  fun toBytes(): ByteArray = toProto().toByteArray()

  fun toProto(): ContextVariableProtos.Value =
    ContextVariableProtos.Value.newBuilder().let { builder ->
      when (value) {
        is Int -> builder.integer = value
        is Float -> builder.float = value
        is Long -> builder.long = value
        is Double -> builder.double = value
        is String -> builder.string = value
        is Boolean -> builder.bool = value
        is ByteArray -> builder.bytes = ByteString.copyFrom(value)
        is Array<*> -> builder.array = toCollectionProto(value.toList())
        is List<*> -> builder.list = toCollectionProto(value)
        is Map<*, *> -> builder.map = toMapProto(value)
        null -> builder.clear()
        else -> error("value type could not be converted to proto")
      }

      builder.build()
    }
}
