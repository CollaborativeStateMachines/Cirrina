package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.csm.Csml.*
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariableBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Expression
import at.ac.uibk.dps.cirrina.execution.`object`.expression.ExpressionBuilder

/**
 * A builder responsible for transforming [ActionDescription]s into executable [Action] objects.
 *
 * @property actionDescription the source description used to construct the action.
 * @property name an optional identifier, required specifically for [TimeoutAction] instances.
 */
class ActionBuilder
private constructor(
  private val actionDescription: ActionDescription,
  private val name: String? = null,
) {

  /**
   * Associates a name with the action being built. Primarily used for tracking and resetting
   * [TimeoutAction]s.
   */
  fun withName(name: String): ActionBuilder = ActionBuilder(actionDescription, name)

  /**
   * Attempts to build an [Action] instance based on the provided description.
   *
   * @return a [Result] containing the built action or an error if validation fails.
   */
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
            "Unknown action type: ${actionDescription.javaClass.simpleName}"
          )
        )
    }

  private fun buildEvalAction(eval: EvalDescription): Result<Action> =
    ExpressionBuilder.from(eval.expression).build().map(::EvalAction)

  private fun buildInvokeAction(invoke: InvokeDescription): Result<Action> = runCatching {
    val input = buildVariableList(invoke.input).getOrThrow()
    val raises = buildEvents(invoke.raises).getOrThrow()
    InvokeAction(invoke.type, invoke.mode, input, raises)
  }

  private fun buildMatchAction(match: MatchDescription): Result<Action> = runCatching {
    val valueExpression = ExpressionBuilder.from(match.value).build().getOrThrow()
    val cases = buildCases(match.cases).getOrThrow()
    val default = match.default?.let { from(it).build().getOrThrow() }
    MatchAction(valueExpression, cases, default)
  }

  private fun buildRaiseAction(raise: RaiseDescription): Result<Action> = runCatching {
    val target = raise.target?.let { ExpressionBuilder.from(raise.target).build().getOrThrow() }
    val event = EventBuilder.from(raise.event).build().getOrThrow()
    RaiseAction(event, target)
  }

  private fun buildTimeoutAction(timeout: TimeoutDescription): Result<Action> = runCatching {
    val actionName = name ?: error("timeout action name is required")
    val delay = ExpressionBuilder.from(timeout.delay).build().getOrThrow()
    val todo = from(timeout.`do`).build().getOrThrow()

    TimeoutAction(actionName, delay, todo)
  }

  private fun buildResetAction(reset: ResetDescription): Result<Action> =
    Result.success(TimeoutResetAction(reset.name))

  private fun buildCases(cases: List<CaseDescription>): Result<Map<Expression, Action>> =
    runCatching {
      cases.associate { case ->
        val of = ExpressionBuilder.from(case.of).build().getOrThrow()
        val then = from(case.then).build().getOrThrow()
        of to then
      }
    }

  companion object {
    /** Creates a new [ActionBuilder] starting from the given [ActionDescription]. */
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
