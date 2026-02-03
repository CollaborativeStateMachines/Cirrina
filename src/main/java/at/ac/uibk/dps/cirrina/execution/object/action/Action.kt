package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.csm.Csml.*
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariableBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.expression.ExpressionBuilder

sealed interface Action {

  companion object {
    fun create(description: ActionDescription, name: String? = null): Result<Action> = runCatching {
      when (description) {
        is EvalDescription ->
          ExpressionBuilder.from(description.expression).build().map(::EvalAction).getOrThrow()

        is InvokeDescription ->
          InvokeAction(
            description.type,
            description.mode,
            buildVariables(description.input).getOrThrow(),
            buildEvents(description.raises).getOrThrow(),
          )

        is MatchDescription ->
          MatchAction(
            ExpressionBuilder.from(description.value).build().getOrThrow(),
            description.cases.associate {
              ExpressionBuilder.from(it.of).build().getOrThrow() to create(it.then).getOrThrow()
            },
            description.default?.let { create(it).getOrThrow() },
          )

        is RaiseDescription ->
          RaiseAction(
            EventBuilder.from(description.event).build().getOrThrow(),
            description.target?.let { ExpressionBuilder.from(it).build().getOrThrow() },
          )

        is TimeoutDescription ->
          TimeoutAction(
            name ?: error("timeout action name required"),
            ExpressionBuilder.from(description.delay).build().getOrThrow(),
            create(description.`do`).getOrThrow(),
          )

        is ResetDescription -> TimeoutResetAction(description.name)

        else ->
          throw UnsupportedOperationException("unknown type: ${description.javaClass.simpleName}")
      }
    }

    private fun buildVariables(context: Map<String, String>) = runCatching {
      context.map { (k, v) ->
        ContextVariableBuilder.empty()
          .name(k)
          .expression(ExpressionBuilder.from(v).build().getOrThrow())
          .build()
          .getOrThrow()
      }
    }

    private fun buildEvents(events: List<EventDescription>) = runCatching {
      events.map { EventBuilder.from(it).build().getOrThrow() }
    }
  }
}
