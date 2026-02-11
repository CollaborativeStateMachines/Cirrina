package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.csm.Csml.StateMachineDescription
import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.EventRaisingAction
import org.jgrapht.graph.DirectedPseudograph

class StateMachine
internal constructor(
  val name: String,
  val nestedStateMachinesSpecs: List<StateMachine>,
  val transientContextDescription: Map<String, String>?,
) : DirectedPseudograph<State, Transition>(Transition::class.java) {
  val initialState: State by lazy {
    vertexSet().firstOrNull { it.initial }
      ?: error("state machine '$name' must have an initial state")
  }

  private val stateNames: Map<String, State> by lazy { vertexSet().associateBy { it.name } }

  private val alwaysTransitions: Map<State, List<Transition>> by lazy {
    vertexSet().associateWith { state -> outgoingEdgesOf(state).filter { it.event == null } }
  }

  private val onTransitions: Map<State, Map<String, List<Transition>>> by lazy {
    vertexSet().associateWith { state ->
      outgoingEdgesOf(state).filter { it.event != null }.groupBy { it.event!! }
    }
  }

  fun getStateClassByName(stateName: String): State? = stateNames[stateName]

  fun getOnTransitionsFromStateByEventName(fromState: State, event: String): List<Transition> =
    onTransitions[fromState]?.get(event).orEmpty()

  fun getAlwaysTransitionsFromState(fromState: State): List<Transition> =
    alwaysTransitions[fromState].orEmpty()

  val inputEvents: List<String> by lazy {
    val localEvents = edgeSet().mapNotNull { it.event }
    val nestedEvents = nestedStateMachinesSpecs.flatMap { it.inputEvents }
    (localEvents + nestedEvents).distinct()
  }

  val outputEvents: List<Event> by lazy {
    val localEvents =
      (vertexSet().flatMap { it.getActionsOfType<EventRaisingAction>() } +
          edgeSet().flatMap { it.getActionsOfType<EventRaisingAction>() })
        .flatMap { it.raises() }

    val nestedEvents = nestedStateMachinesSpecs.flatMap { it.outputEvents }
    (localEvents + nestedEvents).distinct().filter { it.channel != EventChannel.INTERNAL }
  }

  companion object {
    fun create(description: StateMachineDescription, name: String = ""): Result<StateMachine> =
      runCatching {
        val nested =
          description.stateMachines.map { (nestedName, desc) ->
            create(desc, nestedName).getOrThrow()
          }

        val spec = StateMachine(name, nested, description.transient)

        description.states.forEach { (stateName, stateDesc) ->
          spec.addVertex(State.create(stateDesc, stateName).getOrThrow())
        }

        description.states.forEach { (sourceName, stateDesc) ->
          val source = spec.getStateClassByName(sourceName)!!

          stateDesc.on.forEach { (event, transDesc) -> buildGraph(spec, source, event, transDesc) }

          stateDesc.always.forEach { transDesc -> buildGraph(spec, source, null, transDesc) }
        }

        spec
      }

    private fun buildGraph(
      graph: StateMachine,
      source: State,
      event: String?,
      desc: TransitionDescription,
    ) {
      val target =
        desc.to?.let { targetName ->
          graph.getStateClassByName(targetName)
            ?: error("state machine '${graph.name}' has invalid transition target: $targetName")
        } ?: source

      val transition = Transition.create(desc, event).getOrThrow()

      require(graph.addEdge(source, target, transition)) {
        "illegal transition in state machine '${graph.name}' from '${source.name}'"
      }
    }
  }
}
