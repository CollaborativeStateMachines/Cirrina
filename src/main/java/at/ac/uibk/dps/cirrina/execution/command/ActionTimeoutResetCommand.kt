package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutResetAction
import io.micrometer.core.instrument.MeterRegistry

/**
 * A command responsible for resetting an active timeout context as defined by a
 * [timeoutResetAction].
 *
 * This command signals the execution engine to reset the timer for the current scope. Timeout reset
 * actions do not generate further commands.
 *
 * @property timeoutResetAction the specific action definition for the timeout reset.
 * @property executionContext the context in which the evaluation occurs.
 * @property meterRegistry the registry used for collecting metrics.
 */
class ActionTimeoutResetCommand
internal constructor(
  val timeoutResetAction: TimeoutResetAction,
  executionContext: ExecutionContext,
  meterRegistry: MeterRegistry,
) : ActionCommand(executionContext, meterRegistry) {

  /**
   * Executes the timeout reset logic.
   *
   * @return an empty list of [ActionCommand]s.
   * @throws Exception if the command execution fails due to an internal error.
   */
  override fun execute(): List<ActionCommand> = emptyList()
}
