package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.csm.Csml.EventDescription
import at.ac.uibk.dps.cirrina.util.getInsecureUuid
import kotlin.time.Clock

data class Event(
  val topic: String,
  val channel: EventChannel,
  val data: List<ContextVariable> = emptyList(),
  val target: String = "",
  val source: String = "",
  val id: String = getInsecureUuid().toString(),
  val createdTime: Long = Clock.System.now().epochSeconds,
) {
  fun evaluateData(extent: Extent): Event = copy(data = data.map { it.evaluate(extent) })

  override fun toString(): String =
    "${this::class.simpleName}(source='$source', topic='$topic', channel=$channel)"

  companion object {
    fun from(description: EventDescription): Result<Event> = runCatching {
      val variables =
        description.data.map { (name, exprSource) ->
          val expression = Expression.from(exprSource).getOrThrow()
          ContextVariable.lazy(name, expression)
        }
      Event(description.topic, description.channel, variables)
    }
  }
}
