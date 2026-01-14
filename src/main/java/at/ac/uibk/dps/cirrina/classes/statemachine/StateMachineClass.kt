package at.ac.uibk.dps.cirrina.classes.statemachine

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass
import at.ac.uibk.dps.cirrina.execution.`object`.action.EventRaisingAction
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import java.util.UUID
import org.jgrapht.graph.DirectedPseudograph

/**
 * State machine class represents the structure of a state machine.
 *
 * A state machine is a graph consisting of state classes as vertices and transition classes as
 * edges. An edge between two vertices (states) represents a possible transition between the states.
 */
class StateMachineClass internal constructor(parameters: Parameters) :
  DirectedPseudograph<StateClass, TransitionClass>(TransitionClass::class.java) {

  val id: UUID = UUID.randomUUID()

  val name: String = parameters.name
  val localContextDescription: Map<String, String>? = parameters.transientContextDescription
  val nestedStateMachineClasses: List<StateMachineClass> = parameters.nestedStateMachineClasses

  val initialState: StateClass
    get() = vertexSet().first { it.isInitial }

  val inputEvents: List<String>
    get() = edgeSet().mapNotNull { it.event }

  val outputEvents: List<Event>
    get() {
      // Merges actions from both states and transitions to find raised events
      val vertexActions =
        vertexSet().flatMap { it.getActionsOfType(EventRaisingAction::class.java) }
      val edgeActions = edgeSet().flatMap { it.getActionsOfType(EventRaisingAction::class.java) }
      return (vertexActions + edgeActions).flatMap { it.raises() }
    }

  /**
   * Returns a state by its name.
   *
   * @param stateName name of the state to return.
   * @return the state with the supplied name or null.
   */
  fun findStateClassByName(stateName: String): StateClass? {
    val states = vertexSet().filter { it.name == stateName }

    check(states.size <= 1) { "Multiple states found for name: $stateName" }

    return states.firstOrNull()
  }

  /**
   * Returns the transitions from a state that are triggered by a given event name.
   *
   * @param fromStateClass from state.
   * @param event the event.
   * @return the list of on-transitions.
   */
  fun findOnTransitionsFromStateByEventName(
    fromStateClass: StateClass,
    event: String,
  ): List<TransitionClass> = outgoingEdgesOf(fromStateClass).filter { it.event == event }

  /**
   * Returns the transitions from a state that are not event-triggered.
   *
   * @param fromStateClass from state.
   * @return the list of always-transitions.
   */
  fun findAlwaysTransitionsFromState(fromStateClass: StateClass): List<TransitionClass> =
    outgoingEdgesOf(fromStateClass).filter { it.event == null }

  override fun toString(): String = name

  data class Parameters(
    val name: String,
    val transientContextDescription: Map<String, String>?,
    val nestedStateMachineClasses: List<StateMachineClass>,
  )
}
