package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventProtos

object EventExchange {
  fun toBytes(event: Event): ByteArray {
    if (event.data.any { it.isLazy }) {
      error("event '${event.topic}' has unevaluated event data")
    }
    return toProto(event).toByteArray()
  }

  fun toProto(event: Event): EventProtos.Event {
    val channel =
      try {
        EventProtos.Event.Channel.valueOf(event.channel.name)
      } catch (_: Exception) {
        error("event '${event.topic}' has an unrecognized channel: ${event.channel.name}")
      }

    val builder =
      EventProtos.Event.newBuilder()
        .setTopic(event.topic)
        .setChannel(channel)
        .setTarget(event.target)
        .setSource(event.source)
        .setId(event.id)
        .setEmittedTime(event.emittedTime)

    event.data.forEach { variable -> builder.addData(ContextVariableExchange.toProto(variable)) }

    return builder.build()
  }

  fun fromBytes(data: ByteArray): Event {
    val eventProto =
      try {
        EventProtos.Event.parseFrom(data)
      } catch (_: Exception) {
        error("received an event with an unsupported payload")
      }

    return fromProto(eventProto)
  }

  fun fromProto(proto: EventProtos.Event): Event {
    val channel =
      try {
        EventChannel.valueOf(proto.channel.name)
      } catch (_: Exception) {
        error("event has an unrecognized channel: ${proto.channel.name}")
      }

    val data = ArrayList<ContextVariable>(proto.dataCount)
    for (i in 0 until proto.dataCount) {
      data.add(ContextVariableExchange.fromProto(proto.getData(i)))
    }

    return Event(
      proto.topic,
      channel,
      data,
      proto.target,
      proto.source,
      proto.id,
      proto.emittedTime,
    )
  }
}
