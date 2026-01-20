package at.ac.uibk.dps.cirrina.classes.statemachine

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass
import at.ac.uibk.dps.cirrina.execution.`object`.action.EventRaisingAction
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import java.util.UUID
import org.jgrapht.graph.DirectedPseudograph

class StateMachineClass internal constructor(parameters: Parameters) :
  DirectedPseudograph<StateClass, TransitionClass>(TransitionClass::class.java) {

  val id: UUID = UUID.randomUUID()
  val name: String = parameters.name
  val localContextDescription: Map<String, String>? = parameters.transientContextDescription
  val nestedStateMachineClasses: List<StateMachineClass> = parameters.nestedStateMachineClasses

  private val stateNameMap: Map<String, StateClass> by lazy { vertexSet().associateBy { it.name } }

  private val alwaysTransitionsMap: Map<StateClass, List<TransitionClass>> by lazy {
    vertexSet().associateWith { state -> outgoingEdgesOf(state).filter { it.event == null } }
  }

  private val onTransitionsMap: Map<StateClass, Map<String, List<TransitionClass>>> by lazy {
    vertexSet().associateWith { state ->
      outgoingEdgesOf(state).filter { it.event != null }.groupBy { it.event!! }
    }
  }

  val initialState: StateClass by lazy {
    vertexSet().firstOrNull { it.isInitial }
      ?: error("state machine '$name' has no initial state defined")
  }

  fun getStateClassByName(stateName: String): StateClass? = stateNameMap[stateName]

  fun getOnTransitionsFromStateByEventName(
    fromStateClass: StateClass,
    event: String,
  ): List<TransitionClass> = onTransitionsMap[fromStateClass]?.get(event) ?: emptyList()

  fun getAlwaysTransitionsFromState(fromStateClass: StateClass): List<TransitionClass> =
    alwaysTransitionsMap[fromStateClass] ?: emptyList()

  val inputEvents: List<String> by lazy { edgeSet().mapNotNull { it.event }.distinct() }

  val outputEvents: List<Event> by lazy {
    val vertexActions = vertexSet().flatMap { it.getActionsOfType(EventRaisingAction::class.java) }
    val edgeActions = edgeSet().flatMap { it.getActionsOfType(EventRaisingAction::class.java) }
    (vertexActions + edgeActions).flatMap { it.raises() }
  }

  override fun toString(): String = name

  data class Parameters(
    val name: String,
    val transientContextDescription: Map<String, String>?,
    val nestedStateMachineClasses: List<StateMachineClass>,
  )
}
