package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription
import at.ac.uibk.dps.cirrina.execution.`object`.Action
import at.ac.uibk.dps.cirrina.execution.`object`.ActionGraph
import at.ac.uibk.dps.cirrina.execution.`object`.TimeoutAction

class State
private constructor(
  val name: String,
  val initial: Boolean,
  val terminal: Boolean,
  val staticContextDescription: Map<String, String>?,
  entryActions: List<Action>,
  exitActions: List<Action>,
  whileActions: List<Action>,
  afterActions: List<Action>,
) {
  val entryActions = ActionGraph.create(entryActions)
  val exitActions = ActionGraph.create(exitActions)
  val whileActions = ActionGraph.create(whileActions)
  val afterActions = ActionGraph.create(afterActions)

  inline fun <reified T : Action> getActionsOfType(): List<T> =
    listOf(entryActions, exitActions, whileActions, afterActions).flatMap {
      it.getActionsOfType<T>()
    }

  companion object {
    fun create(description: StateDescription, name: String): Result<State> = runCatching {
      State(
        name,
        description.isInitial,
        description.isTerminal,
        description.static,
        resolveActions(description.entry),
        resolveActions(description.exit),
        resolveActions(description.`while`),
        resolveAfterActions(description.after),
      )
    }

    private fun resolveActions(descriptions: List<ActionDescription>): List<Action> =
      descriptions.map { Action.create(it).getOrThrow() }

    private fun resolveAfterActions(descriptions: Map<String, ActionDescription>): List<Action> =
      descriptions
        .map { (name, desc) -> Action.create(desc, name).getOrThrow() }
        .also { actions ->
          require(actions.all { it is TimeoutAction }) {
            "all 'after' actions must be timeout actions"
          }
        }
  }
}
