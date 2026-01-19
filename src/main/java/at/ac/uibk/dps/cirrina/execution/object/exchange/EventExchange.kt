package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

class EventExchange(val event: Event) {

  companion object {
    fun fromBytes(data: ByteArray): Result<EventExchange> =
      runCatching {
          val eventProto = EventProtos.Event.parseFrom(data)
          fromProto(eventProto).getOrThrow()
        }
        .map { EventExchange(it) }
        .recoverCatching { ex ->
          throw UnsupportedOperationException("received an event with an unsupported payload", ex)
        }

    fun fromProto(proto: EventProtos.Event): Result<Event> = runCatching {
      val channel =
        try {
          EventChannel.valueOf(proto.channel.name)
        } catch (e: IllegalArgumentException) {
          throw UnsupportedOperationException("event has an unrecognized channel", e)
        }

      val data =
        proto.dataList.map { contextVariableProto ->
          ContextVariableExchange.fromProto(contextVariableProto).getOrThrow()
        }

      Event(proto.name, channel, data, proto.id, proto.createdTime)
    }
  }

  fun toBytes(): Result<ByteArray> {
    if (event.data.any { it.isLazy }) {
      return Result.failure(
        IllegalStateException("event '${event.name}' has unevaluated event data")
      )
    }

    return toProto().map { it.toByteArray() }
  }

  fun toProto(): Result<EventProtos.Event> = runCatching {
    val channel =
      try {
        EventProtos.Event.Channel.valueOf(event.channel.name)
      } catch (e: IllegalArgumentException) {
        throw UnsupportedOperationException("event '${event.name}' has an unrecognized channel", e)
      }

    val dataProtos =
      event.data.map { variable -> ContextVariableExchange(variable).toProto().getOrThrow() }

    EventProtos.Event.newBuilder()
      .setCreatedTime(event.createdTime)
      .setId(event.id)
      .setName(event.name)
      .setChannel(channel)
      .addAllData(dataProtos)
      .build()
  }
}
