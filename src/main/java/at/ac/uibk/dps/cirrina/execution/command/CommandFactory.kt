package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.EvalAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.InvokeAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.MatchAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.RaiseAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutResetAction
import io.micrometer.core.instrument.MeterRegistry

/**
 * A factory responsible for creating [ActionCommand] instances from [Action] definitions.
 *
 * This factory maps action definitions to their corresponding executable command implementations
 * within the provided [executionContext].
 *
 * @property executionContext the context to be injected into the created commands.
 * @property meterRegistry the registry used for collecting metrics.
 */
class CommandFactory(
  private val executionContext: ExecutionContext,
  private val meterRegistry: MeterRegistry,
) {

  /**
   * Creates an [ActionCommand] for the given [action].
   *
   * @param action the action definition to convert.
   * @return the corresponding [ActionCommand] implementation.
   * @throws Exception if the command execution fails due to an internal error.
   */
  fun createActionCommand(action: Action): ActionCommand =
    when (action) {
      is EvalAction -> ActionEvalCommand(action, executionContext, meterRegistry)
      is InvokeAction -> ActionInvokeCommand(action, executionContext, meterRegistry)
      is MatchAction -> ActionMatchCommand(action, executionContext, meterRegistry)
      is RaiseAction -> ActionRaiseCommand(action, executionContext, meterRegistry)
      is TimeoutAction -> ActionTimeoutCommand(action, executionContext, meterRegistry)
      is TimeoutResetAction -> ActionTimeoutResetCommand(action, executionContext, meterRegistry)
      else -> error("unexpected action type: ${action::class.simpleName}")
    }
}
