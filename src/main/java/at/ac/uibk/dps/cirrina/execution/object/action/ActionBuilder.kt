package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.csm.Csml.*
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariableBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression
import at.ac.uibk.dps.cirrina.execution.`object`.expression.ExpressionBuilder

class ActionBuilder private constructor(private val actionDescription: ActionDescription) {

  private var name: String? = null

  fun withName(name: String): ActionBuilder = apply { this.name = name }

  fun build(): Result<Action> =
    when (actionDescription) {
      is EvalDescription -> buildEvalAction(actionDescription)
      is InvokeDescription -> buildInvokeAction(actionDescription)
      is MatchDescription -> buildMatchAction(actionDescription)
      is RaiseDescription -> buildRaiseAction(actionDescription)
      is TimeoutDescription -> buildTimeoutAction(actionDescription)
      is ResetDescription -> buildResetAction(actionDescription)
      else ->
        Result.failure(
          UnsupportedOperationException(
            "unknown action type: ${actionDescription.javaClass.simpleName}"
          )
        )
    }

  private fun buildEvalAction(eval: EvalDescription): Result<Action> = runCatching {
    val expression = ExpressionBuilder.from(eval.expression).build().getOrThrow()
    EvalAction(EvalAction.Parameters(expression))
  }

  private fun buildInvokeAction(invoke: InvokeDescription): Result<Action> = runCatching {
    val input = buildVariableList(invoke.input).getOrThrow()
    val done = buildEvents(invoke.raises).getOrThrow()
    InvokeAction(InvokeAction.Parameters(invoke.type, invoke.mode, input, done))
  }

  private fun buildMatchAction(match: MatchDescription): Result<Action> = runCatching {
    val valueExpression = ExpressionBuilder.from(match.value).build().getOrThrow()
    val cases = buildCases(match.cases).getOrThrow()
    val defaultAction = match.default?.let { from(it).build().getOrThrow() }

    MatchAction(MatchAction.Parameters(valueExpression, cases, defaultAction))
  }

  private fun buildRaiseAction(raise: RaiseDescription): Result<Action> = runCatching {
    val event = EventBuilder.from(raise.event).build().getOrThrow()
    RaiseAction(RaiseAction.Parameters(event))
  }

  private fun buildTimeoutAction(timeout: TimeoutDescription): Result<Action> = runCatching {
    val actionName = name ?: throw IllegalArgumentException("Timeout action name is not provided")
    val delayExpression = ExpressionBuilder.from(timeout.delay).build().getOrThrow()
    val timeoutAction = from(timeout.`do`).build().getOrThrow()

    TimeoutAction(TimeoutAction.Parameters(actionName, delayExpression, timeoutAction))
  }

  private fun buildResetAction(reset: ResetDescription): Result<Action> = runCatching {
    TimeoutResetAction(TimeoutResetAction.Parameters(reset.name))
  }

  private fun buildCases(cases: List<CaseDescription>): Result<Map<Expression, Action>> =
    runCatching {
      cases.associate { case ->
        val key = ExpressionBuilder.from(case.of).build().getOrThrow()
        val value = from(case.then).build().getOrThrow()
        key to value
      }
    }

  companion object {
    fun from(actionDescription: ActionDescription): ActionBuilder = ActionBuilder(actionDescription)

    private fun buildVariableList(context: Map<String, String>): Result<List<ContextVariable>> =
      runCatching {
        context.map { (key, value) ->
          ContextVariableBuilder.empty()
            .name(key)
            .expression(ExpressionBuilder.from(value).build().getOrThrow())
            .build()
            .getOrThrow()
        }
      }

    private fun buildEvents(eventDescriptions: List<EventDescription>): Result<List<Event>> =
      runCatching {
        eventDescriptions.map { EventBuilder.from(it).build().getOrThrow() }
      }
  }
}
