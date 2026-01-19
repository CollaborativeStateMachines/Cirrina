package at.ac.uibk.dps.cirrina.execution.command

/**
 * An abstract representation of a single unit of work within the execution engine.
 *
 * Subclasses implement specific logic to interact with the [executionContext] and potentially
 * generate later commands to be executed in the future.
 *
 * @property executionContext the context providing access to scope, gauges, and environment data.
 */
abstract class ActionCommand(protected val executionContext: ExecutionContext) {

  /**
   * Executes the internal logic of the command.
   *
   * @return a [Result] containing a list of [ActionCommand]s to be scheduled for execution on
   *   success, or a failure if the command execution encounters an error.
   */
  abstract fun execute(): Result<List<ActionCommand>>
}
