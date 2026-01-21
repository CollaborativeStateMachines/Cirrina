package at.ac.uibk.dps.cirrina.classes.transition

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraph
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraphBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.guard.Guard
import org.jgrapht.graph.DefaultEdge

/**
 * Represents a transition between two states in the state machine.
 *
 * @property event the name of the event that triggers this transition.
 * @property to the name of the target state.
 * @property iif an optional [Guard] condition.
 * @property do the list of actions to be executed during the transition.
 * @property or an optional alternative state.
 */
open class TransitionClass
internal constructor(
  val event: String?,
  val to: String?,
  val iif: Guard?,
  val `do`: List<Action>,
  val or: String?,
) : DefaultEdge() {

  /** The executable representation of the transition's behavior. */
  val actionGraph: ActionGraph = ActionGraphBuilder.from(`do`).build()

  /**
   * Evaluates whether this transition is eligible to fire based on the current context.
   *
   * @param extent the scope used for guard evaluation.
   * @return true if no guard is defined or if the [iif] guard evaluates to true; false otherwise.
   */
  fun evaluate(extent: Extent): Boolean = iif?.evaluate(extent) ?: true

  public override fun getSource(): StateClass = super.getSource() as StateClass

  public override fun getTarget(): StateClass = super.getTarget() as StateClass

  /**
   * Filters and returns actions of a specific type [T].
   *
   * @return a list of actions matching type [T].
   */
  inline fun <reified T> getActionsOfType(): List<T> = actionGraph.getActionsOfType<T>()
}
