package at.ac.uibk.dps.cirrina.execution.command

/**
 * An abstract representation of a single unit of work within the execution engine.
 *
 * Subclasses implement specific logic to interact with the [executionContext] and generate
 * subsequent commands to be executed.
 *
 * @property executionContext the context providing access to scope, gauges, and environment data.
 */
abstract class ActionCommand(protected val executionContext: ExecutionContext) {

  /**
   * Executes the internal logic of the command.
   *
   * @return a list of [ActionCommand]s to be executed.
   * @throws Exception if the command execution fails due to an internal error.
   */
  abstract fun execute(): List<ActionCommand>
}
