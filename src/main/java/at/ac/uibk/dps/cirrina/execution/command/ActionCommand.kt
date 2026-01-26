package at.ac.uibk.dps.cirrina.execution.command

import io.micrometer.core.instrument.MeterRegistry

/**
 * An abstract representation of a single unit of work within the execution engine.
 *
 * Subclasses implement specific logic to interact with the [executionContext] and generate
 * subsequent commands to be executed.
 *
 * @property executionContext the context providing access to scope, gauges, and environment data.
 * @property meterRegistry the registry used for collecting metrics.
 */
abstract class ActionCommand
internal constructor(
  protected val executionContext: ExecutionContext,
  protected val meterRegistry: MeterRegistry,
) {

  /**
   * Executes the internal logic of the command.
   *
   * @return a list of [ActionCommand]s to be executed.
   * @throws Exception if the command execution fails due to an internal error.
   */
  abstract fun execute(): List<ActionCommand>
}
