package at.ac.uibk.dps.cirrina.execution.command

abstract class ActionCommand(protected val executionContext: ExecutionContext) {

  abstract fun execute(): Result<List<ActionCommand>>
}
