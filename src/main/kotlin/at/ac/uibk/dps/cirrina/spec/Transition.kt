package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription
import at.ac.uibk.dps.cirrina.execution.graph.ActionGraph
import at.ac.uibk.dps.cirrina.execution.`object`.Action
import at.ac.uibk.dps.cirrina.execution.`object`.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.Guard
import org.jgrapht.graph.DefaultEdge

class Transition
private constructor(
  val parent: StateMachine,
  val event: String?,
  description: TransitionDescription,
) : DefaultEdge() {

  val to = description.to

  val provided = description.provided?.let { Guard.from(it) }

  val yields = description.yields.map { Action.create(it) }

  val or = description.or

  val actions: ActionGraph = ActionGraph.create(yields)

  fun evaluate(extent: Extent): Boolean = provided?.evaluate(extent) ?: true

  public override fun getSource(): State = super.getSource() as State

  public override fun getTarget(): State = super.getTarget() as State

  inline fun <reified T : Action> getActionsOfType(): List<T> = actions.getActionsOfType<T>()

  companion object {
    fun create(
      parent: StateMachine,
      description: TransitionDescription,
      event: String? = null,
    ): Result<Transition> = runCatching { Transition(parent, event, description) }
  }
}
