package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraph
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraphBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.guard.Guard
import at.ac.uibk.dps.cirrina.execution.`object`.guard.GuardBuilder
import org.jgrapht.graph.DefaultEdge

class Transition
private constructor(
  val event: String?,
  val to: String?,
  val iif: Guard?,
  val `do`: List<Action>,
  val or: String?,
) : DefaultEdge() {

  val actions: ActionGraph = ActionGraphBuilder.from(`do`).build()

  fun evaluate(extent: Extent): Boolean = iif?.evaluate(extent) ?: true

  public override fun getSource(): State = super.getSource() as State

  public override fun getTarget(): State = super.getTarget() as State

  inline fun <reified T> getActionsOfType(): List<T> = actions.getActionsOfType<T>()

  companion object {
    fun create(description: TransitionDescription, event: String? = null): Result<Transition> =
      runCatching {
        Transition(
          event,
          description.to,
          description.iif?.let { GuardBuilder.from(it).build().getOrThrow() },
          description.`do`.map { ActionBuilder.from(it).build().getOrThrow() },
          description.or,
        )
      }
  }
}
