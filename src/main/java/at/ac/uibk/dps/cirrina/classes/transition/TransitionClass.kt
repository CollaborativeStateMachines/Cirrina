package at.ac.uibk.dps.cirrina.classes.transition

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraph
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraphBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.guard.Guard
import org.jgrapht.graph.DefaultEdge

open class TransitionClass internal constructor(parameters: Parameters) : DefaultEdge() {

  val to: String? = parameters.to
  val `do`: List<Action> = parameters.`do`
  val iif: Guard? = parameters.iif
  val or: String? = parameters.or
  val event: String? = parameters.event

  val actionGraph: ActionGraph = ActionGraphBuilder.from(`do`).build()

  fun evaluate(extent: Extent): Boolean {
    val guard = iif ?: return true

    return guard.evaluate(extent)
  }

  public override fun getSource(): StateClass = super.getSource() as StateClass

  public override fun getTarget(): StateClass = super.getTarget() as StateClass

  fun <T> getActionsOfType(type: Class<T>): List<T> = actionGraph.getActionsOfType(type)

  data class Parameters(
    val to: String?,
    val `do`: List<Action>,
    val iif: Guard?,
    val or: String?,
    val event: String?,
  )
}
