package at.ac.uibk.dps.cirrina.classes.collaborativestatemachine

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import org.jgrapht.graph.DirectedPseudograph

/**
 * Collaborative state machine class, describes the structure of a collaborative state machine.
 *
 * A collaborative state machine is a graph with state machine classes as vertices and events as
 * edges. An edge in the collaborative state machine graph represents how a state machine can be
 * activated by another state machine based on an event.
 */
class CollaborativeStateMachineClass
internal constructor(
  /** The collection of persistent context variables. */
  val persistentContextVariables: List<ContextVariable>
) : DirectedPseudograph<StateMachineClass, Event>(Event::class.java) {

  /**
   * Returns a state machine class by its name.
   *
   * @param name name of the state machine to return.
   * @return the state machine with the supplied name or null if none or multiple are found.
   */
  fun findStateMachineClassByName(name: String): StateMachineClass? {
    val matches = vertexSet().filter { it.name == name }
    return matches.singleOrNull()
  }

  /** Returns the collection of state machine classes. */
  val stateMachineClasses: List<StateMachineClass>
    get() = vertexSet().toList()
}
