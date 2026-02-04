package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.csm.Csml.*
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression

interface Action {

  companion object {
    fun create(description: ActionDescription, name: String? = null): Result<Action> = runCatching {
      when (description) {
        is EvalDescription -> Expression.from(description.expression).map(::EvalAction).getOrThrow()

        is InvokeDescription ->
          InvokeAction(
            description.type,
            description.mode,
            buildVariables(description.input).getOrThrow(),
            buildEvents(description.raises).getOrThrow(),
          )

        is MatchDescription ->
          MatchAction(
            Expression.from(description.value).getOrThrow(),
            description.cases.associate {
              Expression.from(it.of).getOrThrow() to create(it.then).getOrThrow()
            },
            description.default?.let { create(it).getOrThrow() },
          )

        is RaiseDescription ->
          RaiseAction(
            Event.from(description.event).getOrThrow(),
            description.target?.let { Expression.from(it).getOrThrow() },
          )

        is TimeoutDescription ->
          TimeoutAction(
            name ?: error("timeout action name required"),
            Expression.from(description.delay).getOrThrow(),
            create(description.`do`).getOrThrow(),
          )

        is ResetDescription -> TimeoutResetAction(description.name)

        else -> error("unknown type: ${description.javaClass.simpleName}")
      }
    }

    private fun buildVariables(context: Map<String, String>) = runCatching {
      context.map { (k, v) ->
        val expression = Expression.from(v).getOrThrow()
        ContextVariable.lazy(k, expression)
      }
    }

    private fun buildEvents(events: List<EventDescription>) = runCatching {
      events.map { Event.from(it).getOrThrow() }
    }
  }
}

interface EventRaisingAction : Action {

  fun raises(): List<Event>
}

class EvalAction internal constructor(val expression: Expression) : Action

class InvokeAction
internal constructor(
  val type: String,
  val mode: InvocationMode,
  val input: List<ContextVariable>,
  val raises: List<Event>,
) : EventRaisingAction {

  override fun raises(): List<Event> = raises
}

class MatchAction
internal constructor(
  val value: Expression,
  val cases: Map<Expression, Action>,
  val default: Action? = null,
) : EventRaisingAction {

  override fun raises(): List<Event> =
    (cases.values + listOfNotNull(default)).filterIsInstance<EventRaisingAction>().flatMap {
      it.raises()
    }
}

class RaiseAction internal constructor(val event: Event, val target: Expression?) :
  EventRaisingAction {

  override fun raises(): List<Event> = listOf(event)
}

class TimeoutAction
internal constructor(val name: String, val delay: Expression, val `do`: Action) :
  EventRaisingAction {

  override fun raises(): List<Event> =
    (`do` as? RaiseAction)?.let { listOf(it.event) } ?: emptyList()
}

class TimeoutResetAction internal constructor(val action: String) : Action
