package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription
import at.ac.uibk.dps.cirrina.execution.graph.ActionGraph
import at.ac.uibk.dps.cirrina.execution.`object`.Action
import at.ac.uibk.dps.cirrina.execution.`object`.TimeoutAction

class State
private constructor(val parent: StateMachine, val name: String, description: StateDescription) {
  val initial = description.isInitial
  val terminal = description.isTerminal
  val static = description.static

  val entry = ActionGraph.create(resolveActions(description.entry))
  val exit = ActionGraph.create(resolveActions(description.exit))
  val during = ActionGraph.create(resolveActions(description.during))
  val after = ActionGraph.create(resolveAfterActions(description.after))

  inline fun <reified T : Action> getActionsOfType() =
    listOf(entry, exit, during, after).flatMap { it.getActionsOfType<T>() }

  companion object {
    fun create(parent: StateMachine, description: StateDescription, name: String) = runCatching {
      State(parent, name, description)
    }

    private fun resolveActions(descriptions: List<ActionDescription>) =
      descriptions.map { Action.create(it) }

    private fun resolveAfterActions(descriptions: Map<String, ActionDescription>) =
      descriptions
        .map { (name, desc) -> Action.create(desc, name) }
        .also { actions ->
          require(actions.all { it is TimeoutAction }) {
            "all 'after' actions must be timeout actions"
          }
        }
  }
}
