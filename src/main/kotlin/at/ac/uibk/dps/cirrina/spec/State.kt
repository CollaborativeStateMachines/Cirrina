package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription
import at.ac.uibk.dps.cirrina.spec.graph.ActionGraph

class State private constructor(val name: String, csml: Csml, description: StateDescription) {
  /** The boolean indicating the initial status */
  val initial = description.isInitial

  /** The boolean indicating the terminal status */
  val terminal = description.isTerminal

  /** The static data context variables */
  val static: Map<String, Expression> = description.static.mapValues { (_, v) -> Expression(v) }

  val entry = ActionGraph.create(resolveActions(csml, description.entry))
  val exit = ActionGraph.create(resolveActions(csml, description.exit))
  val during = ActionGraph.create(resolveActions(csml, description.during))
  val after = ActionGraph.create(resolveAfterActions(csml, description.after))

  inline fun <reified T : Action> getActionsOfType() =
    listOf(entry, exit, during, after).flatMap { it.getActionsOfType<T>() }

  companion object {
    fun create(csml: Csml, description: StateDescription, name: String) = runCatching {
      State(name, csml, description)
    }

    private fun resolveActions(csml: Csml, descriptions: List<ActionDescription>) =
      descriptions.map { Action.create(csml, it) }

    private fun resolveAfterActions(csml: Csml, descriptions: Map<String, ActionDescription>) =
      descriptions
        .map { (name, desc) -> Action.create(csml, desc, name) }
        .also { actions ->
          require(actions.all { it is Timeout }) { "all 'after' actions must be timeout actions" }
        }
  }
}
