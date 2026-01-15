package at.ac.uibk.dps.cirrina.classes.transition

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraph
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionGraphBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.guard.Guard
import org.jgrapht.graph.DefaultEdge

/**
 * TransitionClass represents a transition that can be 'taken' between two states in a state
 * machine.
 *
 * The base transition is not event-triggered, meaning that it is 'taken' whenever the guards all
 * produce a true value. Actions can be executed during a transition.
 *
 * The else property of the transition represents the target state whenever the transition is not
 * taken (at least one of the guards do not produce a true value).
 */
open class TransitionClass internal constructor(parameters: Parameters) : DefaultEdge() {

  val to: String? = parameters.to
  val `do`: List<Action> = parameters.`do`
  val iif: Guard? = parameters.iif
  val or: String? = parameters.or
  val event: String? = parameters.event

  val actionGraph: ActionGraph = ActionGraphBuilder.from(`do`).build()

  /**
   * Evaluates all guards of this transition.
   *
   * @param extent extent describing variables in scope.
   * @return true if the transition can be taken based on the guards, otherwise false.
   * @throws UnsupportedOperationException if the transition could not be evaluated.
   */
  @Throws(UnsupportedOperationException::class)
  fun evaluate(extent: Extent): Boolean {
    // Evaluate the guard; if the guard returns false, the transition cannot be taken
    return try {
      iif?.evaluate(extent) ?: true
    } catch (e: Exception) {
      when (e) {
        is IllegalArgumentException,
        is UnsupportedOperationException ->
          throw UnsupportedOperationException("transition could not be evaluated", e)
        else -> throw e
      }
    }
  }

  /**
   * Returns the source state of the transition.
   *
   * @return the source state.
   */
  public override fun getSource(): StateClass = super.getSource() as StateClass

  /**
   * Returns the target state of the transition.
   *
   * @return the target state.
   */
  public override fun getTarget(): StateClass = super.getTarget() as StateClass

  /**
   * Returns actions contained in the action graph by type.
   *
   * @param type type to return.
   * @return list of actions matching the specified type.
   */
  fun <T> getActionsOfType(type: Class<T>): List<T> = actionGraph.getActionsOfType(type)

  data class Parameters(
    val to: String?,
    val `do`: List<Action>,
    val iif: Guard?,
    val or: String?,
    val event: String?,
  )
}
