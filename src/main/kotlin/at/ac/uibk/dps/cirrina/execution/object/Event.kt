package at.ac.uibk.dps.cirrina.execution.`object`

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
  fun evaluateData(extent: Extent): Event = copy(data = data.map { it.evaluate(extent) })

  override fun toString(): String =
    "${this::class.simpleName}(source='$source', topic='$topic', channel='$channel')"

  companion object {
    fun from(description: EventDescription): Event {
      val variables =
        description.data.map { (name, exprSource) ->
          val expression = Expression.create(exprSource)
          ContextVariable.lazy(name, expression)
        }
      return Event(description.topic, description.channel, variables)
    }
  }
}
