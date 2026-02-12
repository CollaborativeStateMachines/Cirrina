package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription
import at.ac.uibk.dps.cirrina.execution.graph.ActionGraph
import at.ac.uibk.dps.cirrina.execution.`object`.Action
import at.ac.uibk.dps.cirrina.execution.`object`.TimeoutAction

class State
private constructor(
  val name: String,
  val initial: Boolean,
  val terminal: Boolean,
  val staticContext: Map<String, String>?,
  entry: List<Action>,
  exit: List<Action>,
  `while`: List<Action>,
  after: List<Action>,
) {
  val entry = ActionGraph.create(entry)
  val exit = ActionGraph.create(exit)
  val `while` = ActionGraph.create(`while`)
  val after = ActionGraph.create(after)

  inline fun <reified T : Action> getActionsOfType(): List<T> =
    listOf(entry, exit, `while`, after).flatMap { it.getActionsOfType<T>() }

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
      descriptions.map { Action.create(it) }

    private fun resolveAfterActions(descriptions: Map<String, ActionDescription>): List<Action> =
      descriptions
        .map { (name, desc) -> Action.create(desc, name) }
        .also { actions ->
          require(actions.all { it is TimeoutAction }) {
            "all 'after' actions must be timeout actions"
          }
        }
  }
}
