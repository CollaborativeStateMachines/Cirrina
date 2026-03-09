package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos.Value.ValueCase
import com.google.protobuf.ByteString

object ValueExchange {
  fun toBytes(value: Any?): ByteArray = toProto(value).toByteArray()

  fun toProto(value: Any?): ContextVariableProtos.Value {
    val builder = ContextVariableProtos.Value.newBuilder()
    when (value) {
      is Int -> builder.integer = value
      is Float -> builder.float = value
      is Long -> builder.long = value
      is Double -> builder.double = value
      is String -> builder.string = value
      is Boolean -> builder.bool = value
      is ByteArray -> builder.bytes = ByteString.copyFrom(value)
      is Array<*> -> {
        val colBuilder = ContextVariableProtos.ValueCollection.newBuilder()
        value.forEach { colBuilder.addEntry(toProto(it)) }
        builder.array = colBuilder.build()
      }
      is List<*> -> {
        val colBuilder = ContextVariableProtos.ValueCollection.newBuilder()
        value.forEach { colBuilder.addEntry(toProto(it)) }
        builder.list = colBuilder.build()
      }
      is Map<*, *> -> {
        val mapBuilder = ContextVariableProtos.ValueMap.newBuilder()
        value.forEach { (key, v) ->
          mapBuilder.addEntry(
            ContextVariableProtos.ValueMapEntry.newBuilder()
              .setKey(toProto(key))
              .setValue(toProto(v))
              .build()
          )
        }
        builder.map = mapBuilder.build()
      }
      null -> builder.clear()
      else -> error("value type could not be converted to proto")
    }

    return builder.build()
  }

  fun fromBytes(data: ByteArray): Any? = fromProto(ContextVariableProtos.Value.parseFrom(data))

  fun fromProto(proto: ContextVariableProtos.Value): Any? =
    when (proto.valueCase) {
      ValueCase.INTEGER -> proto.integer
      ValueCase.FLOAT -> proto.float
      ValueCase.LONG -> proto.long
      ValueCase.DOUBLE -> proto.double
      ValueCase.STRING -> proto.string
      ValueCase.BOOL -> proto.bool
      ValueCase.BYTES -> proto.bytes.toByteArray()
      ValueCase.ARRAY -> {
        val list = proto.array.entryList
        val arr = arrayOfNulls<Any>(list.size)
        for (i in list.indices) {
          arr[i] = fromProto(list[i])
        }
        arr
      }
      ValueCase.LIST -> {
        val list = proto.list.entryList
        val res = ArrayList<Any?>(list.size)
        for (i in list.indices) {
          res.add(fromProto(list[i]))
        }
        res
      }
      ValueCase.MAP -> {
        val entries = proto.map.entryList
        val map = HashMap<Any?, Any?>(entries.size)
        for (i in entries.indices) {
          val entry = entries[i]
          map[fromProto(entry.key)] = fromProto(entry.value)
        }
        map
      }
      ValueCase.VALUE_NOT_SET,
      null -> null
    }
}
