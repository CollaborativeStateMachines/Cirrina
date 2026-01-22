package at.ac.uibk.dps.cirrina.classes.statemachine

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass
import at.ac.uibk.dps.cirrina.execution.`object`.action.EventRaisingAction
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import org.jgrapht.graph.DirectedPseudograph

/**
 * An immutable blueprint representing the structure and behavior of a state machine.
 *
 * This class extends [DirectedPseudograph] to define states as vertices and transitions as edges.
 * It provides lookups for transitions based on events and state context.
 *
 * @property name the unique identifier of the state machine.
 * @property nestedStateMachineClasses a list of nested machines contained within this machine.
 * @property transientContextDescription mapping of transient context variables.
 */
class StateMachineClass
internal constructor(
  val name: String,
  val nestedStateMachineClasses: List<StateMachineClass>,
  val transientContextDescription: Map<String, String>?,
) : DirectedPseudograph<StateClass, TransitionClass>(TransitionClass::class.java) {

  /**
   * The designated entry point of the state machine.
   *
   * @throws Exception if no state is marked as initial.
   */
  val initialState: StateClass by lazy {
    vertexSet().firstOrNull { it.initial }
      ?: error("state machine '$name' must have an initial state")
  }

  private val stateNameMap: Map<String, StateClass> by lazy { vertexSet().associateBy { it.name } }

  private val alwaysTransitionsMap: Map<StateClass, List<TransitionClass>> by lazy {
    vertexSet().associateWith { state -> outgoingEdgesOf(state).filter { it.event == null } }
  }

  private val onTransitionsMap: Map<StateClass, Map<String, List<TransitionClass>>> by lazy {
    vertexSet().associateWith { state ->
      outgoingEdgesOf(state).filter { it.event != null }.groupBy { it.event!! }
    }
  }

  /** Resolves a state by its unique name. */
  fun getStateClassByName(stateName: String): StateClass? = stateNameMap[stateName]

  /** Returns a list of transitions originating from [fromStateClass] triggered by [event]. */
  fun getOnTransitionsFromStateByEventName(
    fromStateClass: StateClass,
    event: String,
  ): List<TransitionClass> = onTransitionsMap[fromStateClass]?.get(event).orEmpty()

  /** Returns a list of transitions originating from [fromStateClass] that require no event. */
  fun getAlwaysTransitionsFromState(fromStateClass: StateClass): List<TransitionClass> =
    alwaysTransitionsMap[fromStateClass].orEmpty()

  /** All unique event names that can trigger a transition within this machine. */
  val inputEvents: List<String> by lazy { edgeSet().mapNotNull { it.event }.distinct() }

  /**
   * All events potentially produced by actions within states or along transitions. This traverses
   * all [EventRaisingAction] instances in the graph.
   */
  val outputEvents: List<Event> by lazy {
    (vertexSet().flatMap { it.getActionsOfType<EventRaisingAction>() } +
        edgeSet().flatMap { it.getActionsOfType<EventRaisingAction>() })
      .flatMap { it.raises() }
  }
}
