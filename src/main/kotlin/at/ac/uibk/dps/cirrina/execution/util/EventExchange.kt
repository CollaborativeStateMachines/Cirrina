package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventProtos

class EventExchange(val event: Event) {

  fun toBytes(): ByteArray {
    if (event.data.any { it.isLazy }) {
      error("event '${event.topic}' has unevaluated event data")
    }

    return toProto().toByteArray()
  }

  fun toProto(): EventProtos.Event {
    val channel =
      try {
        EventProtos.Event.Channel.valueOf(event.channel.name)
      } catch (_: Exception) {
        error("event '${event.topic}' has an unrecognized channel: ${event.channel.name}")
      }

    val dataProtos = event.data.map { variable -> ContextVariableExchange(variable).toProto() }

    return EventProtos.Event.newBuilder()
      .setTopic(event.topic)
      .setChannel(channel)
      .addAllData(dataProtos)
      .setTarget(event.target)
      .setSource(event.source)
      .setId(event.id)
      .setCreatedTime(event.createdTime)
      .build()
  }

  companion object {
    fun fromBytes(data: ByteArray): EventExchange {
      val eventProto =
        try {
          EventProtos.Event.parseFrom(data)
        } catch (_: Exception) {
          error("received an event with an unsupported payload")
        }

      return EventExchange(fromProto(eventProto))
    }

    fun fromProto(proto: EventProtos.Event): Event {
      val channel =
        try {
          EventChannel.valueOf(proto.channel.name)
        } catch (_: Exception) {
          error("event has an unrecognized channel: ${proto.channel.name}")
        }

      val data =
        proto.dataList.map { contextVariableProto ->
          ContextVariableExchange.fromProto(contextVariableProto)
        }

      return Event(
        proto.topic,
        channel,
        data,
        proto.target,
        proto.source,
        proto.id,
        proto.createdTime,
      )
    }
  }
}
