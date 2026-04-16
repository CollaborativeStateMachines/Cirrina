package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.ConditionalEventDescription
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.csm.Csml.EventDescription
import at.ac.uibk.dps.cirrina.util.getInsecureUuid

data class Event(
  val topic: String,
  val channel: EventChannel,
  val data: List<ContextVariable> = emptyList(),
  val target: String = "",
  val source: String = "",
  val id: String = getInsecureUuid().toString(),
  val emittedTime: Long = 0L,
) {
  override fun toString(): String =
    "${this::class.simpleName}(source='$source', topic='$topic', channel='$channel')"

  companion object {
    fun from(description: EventDescription): Event {
      val variables =
        description.data.map { (name, exprSource) -> LazyContextVariable(name, exprSource) }
      return Event(description.topic, description.channel, variables)
    }
  }
}

data class ConditionalEvent(val provided: String?, val event: Event) {
  override fun toString(): String =
    "${this::class.simpleName}(provided='$provided', event='$event')"

  companion object {
    fun from(description: ConditionalEventDescription): ConditionalEvent {
      return ConditionalEvent(description.provided, Event.from(description.event))
    }
  }
}
