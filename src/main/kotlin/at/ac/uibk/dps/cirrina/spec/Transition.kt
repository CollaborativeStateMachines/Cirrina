package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription
import at.ac.uibk.dps.cirrina.spec.graph.ActionGraph
import org.jgrapht.graph.DefaultEdge

class Transition
private constructor(val event: String?, csml: Csml, description: TransitionDescription) :
  DefaultEdge() {
  /** The target state name. */
  val to = description.to

  /** The transition guard. */
  val provided = description.provided?.let { Expression(it) }

  val yields = ActionGraph.create(description.yields.map { Action.create(csml, it) })

  /** The alternate target state name. */
  val or = description.or

  public override fun getSource(): State = super.getSource() as State

  public override fun getTarget(): State = super.getTarget() as State

  inline fun <reified T : Action> getActionsOfType(): List<T> = yields.getActionsOfType<T>()

  companion object {
    fun create(
      csml: Csml,
      description: TransitionDescription,
      event: String? = null,
    ): Result<Transition> = runCatching { Transition(event, csml, description) }
  }
}
