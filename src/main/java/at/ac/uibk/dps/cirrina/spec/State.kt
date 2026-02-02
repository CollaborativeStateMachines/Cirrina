package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraphBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction

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
  val entryActions = ActionGraphBuilder.from(entryActions).build()
  val exitActions = ActionGraphBuilder.from(exitActions).build()
  val whileActions = ActionGraphBuilder.from(whileActions).build()
  val afterActions = ActionGraphBuilder.from(afterActions).build()

  inline fun <reified T> getActionsOfType(): List<T> =
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
      descriptions.map { ActionBuilder.from(it).build().getOrThrow() }

    private fun resolveAfterActions(descriptions: Map<String, ActionDescription>): List<Action> =
      descriptions
        .map { (name, desc) -> ActionBuilder.from(desc).withName(name).build().getOrThrow() }
        .also { actions ->
          require(actions.all { it is TimeoutAction }) {
            "all 'after' actions must be timeout actions"
          }
        }
  }
}
