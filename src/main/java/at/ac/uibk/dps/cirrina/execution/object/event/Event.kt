package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.utils.Uuid.insecureUuid
import kotlin.time.Clock
import org.apache.commons.lang3.builder.ToStringBuilder

data class Event(
  val name: String,
  val channel: EventChannel,
  val data: List<ContextVariable> = emptyList(),
  val id: String = insecureUuid().toString(),
  val createdTime: Long = Clock.System.now().epochSeconds,
) {
  fun evaluateData(extent: Extent): Event {
    val evaluatedData = data.map { variable -> variable.evaluate(extent) }
    return copy(data = evaluatedData)
  }

  fun withData(data: List<ContextVariable>): Event {
    return Event(name, channel, data)
  }

  override fun toString(): String =
    ToStringBuilder(this).append("id", id).append("channel", channel).toString()

  companion object {
    fun ensureHasEvaluatedData(event: Event, extent: Extent): Event = event.evaluateData(extent)
  }
}
