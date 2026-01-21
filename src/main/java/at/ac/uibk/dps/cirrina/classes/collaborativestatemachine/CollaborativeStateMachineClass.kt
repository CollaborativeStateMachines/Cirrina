package at.ac.uibk.dps.cirrina.classes.collaborativestatemachine

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import org.jgrapht.graph.DirectedPseudograph

/** Represents the structure of a collaborative state machine. */
class CollaborativeStateMachineClass
internal constructor(val persistentContextVariables: List<ContextVariable>) :
  DirectedPseudograph<StateMachineClass, Event>(Event::class.java) {

  /**
   * Returns a state machine class by its name.
   *
   * @param name name of the state machine to return.
   * @return the state machine with the supplied name or null if none or multiple are found.
   */
  fun findStateMachineClassByName(name: String): StateMachineClass? {
    return vertexSet().singleOrNull { it.name == name }
  }
}
