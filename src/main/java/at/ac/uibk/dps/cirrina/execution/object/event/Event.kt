package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.utils.getInsecureUuid
import kotlin.time.Clock
import org.apache.commons.lang3.builder.ToStringBuilder

data class Event(
  val topic: String,
  val channel: EventChannel,
  val data: List<ContextVariable> = emptyList(),
  val source: String? = null,
  val id: String = getInsecureUuid().toString(),
  val createdTime: Long = Clock.System.now().epochSeconds,
) {
  fun evaluateData(extent: Extent): Event {
    val evaluatedData = data.map { variable -> variable.evaluate(extent) }
    return copy(data = evaluatedData)
  }

  fun withData(data: List<ContextVariable>): Event {
    return Event(topic, channel, data, source, id, createdTime)
  }

  fun withSource(source: String): Event {
    return Event(topic, channel, data, source, id, createdTime)
  }

  override fun toString(): String =
    ToStringBuilder(this)
      .append("source", source)
      .append("topic", topic)
      .append("channel", channel)
      .toString()

  companion object {
    fun ensureHasEvaluatedData(event: Event, extent: Extent): Event = event.evaluateData(extent)
  }
}
