package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.MatchAction
import com.google.common.flogger.FluentLogger

class ActionMatchCommand(executionContext: ExecutionContext, private val matchAction: MatchAction) :
  ActionCommand(executionContext) {

  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }

  override fun execute(): Result<List<ActionCommand>> =
    runCatching {
        val extent = executionContext.scope.getExtent()
        val commandFactory = CommandFactory(executionContext)

        // Evaluate the main match condition
        val conditionValue = matchAction.value.execute(extent).getOrThrow()

        // Filter for matching cases
        val matchingActions =
          matchAction.`case`.entries
            .filter { (expression, _) ->
              val caseValue = expression.execute(extent).getOrThrow()
              conditionValue == caseValue
            }
            .map { it.value }

        val finalActions =
          when {
            matchingActions.isNotEmpty() -> matchingActions
            else -> listOf(matchAction.`default`)
          }

        // Convert actions to commands
        finalActions.map { commandFactory.createActionCommand(it) }
      }
      .recoverCatching { ex ->
        logger.atWarning().withCause(ex).log("could not execute match action")
        throw UnsupportedOperationException("could not execute match action", ex)
      }
}
