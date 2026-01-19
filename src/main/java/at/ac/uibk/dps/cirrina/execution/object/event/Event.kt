package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.utils.Time
import at.ac.uibk.dps.cirrina.utils.Uuid.insecureUuid

data class Event(
  val name: String,
  val channel: EventChannel,
  val data: List<ContextVariable> = emptyList(),
  val id: String = insecureUuid().toString(),
  val createdTime: Double = Time.timeInMillisecondsSinceEpoch(),
) {
  fun evaluateData(extent: Extent): Event {
    val evaluatedData =
      data.map { variable ->
        variable.evaluate(extent).getOrElse { ex ->
          throw UnsupportedOperationException(
            "The event data variable '${variable.name}' could not be evaluated",
            ex,
          )
        }
      }
    return copy(data = evaluatedData)
  }

  fun withData(data: List<ContextVariable>): Event {
    return Event(name, channel, data)
  }

  override fun toString(): String =
    "Event(id='$id', name='$name', channel=$channel, createdTime=$createdTime)"

  companion object {
    fun ensureHasEvaluatedData(event: Event, extent: Extent): Event = event.evaluateData(extent)
  }
}
