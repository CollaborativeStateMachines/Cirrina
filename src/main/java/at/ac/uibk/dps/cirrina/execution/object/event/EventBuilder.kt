package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml.EventDescription
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariableBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.expression.ExpressionBuilder

class EventBuilder private constructor(private val eventDescription: EventDescription) {

  companion object {
    @JvmStatic
    fun from(eventDescription: EventDescription): EventBuilder = EventBuilder(eventDescription)

    private fun buildVariableList(contextMap: Map<String, String>): Result<List<ContextVariable>> {
      return runCatching {
        contextMap.map { (name, expressionSource) ->
          val expression = ExpressionBuilder.from(expressionSource).build().getOrThrow()

          ContextVariableBuilder.empty().name(name).expression(expression).build().getOrThrow()
        }
      }
    }
  }

  fun build(): Result<Event> {
    return buildVariableList(eventDescription.data).map { variables ->
      Event(name = eventDescription.topic, channel = eventDescription.channel, data = variables)
    }
  }
}
