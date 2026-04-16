package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.csm.Csml.StateMachineDescription
import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription
import org.jgrapht.graph.DirectedPseudograph

class StateMachine
internal constructor(
  csml: Csml,
  val name: String,
  val nested: List<StateMachine>,
  description: StateMachineDescription,
) : DirectedPseudograph<State, Transition>(Transition::class.java) {
  /** The transient data context variables */
  val transient: Map<String, String>? = description.transient

  /** The initial state specification. */
  val initial: State by lazy {
    vertexSet().firstOrNull { it.initial }
      ?: error("state machine '$name' must have an initial state")
  }

  /** The list of input events (the event topics this state machine responds to). */
  val inputEvents: List<String> by lazy {
    val localEvents = edgeSet().mapNotNull { it.event }
    val nestedEvents = nested.flatMap { it.inputEvents }
    (localEvents + nestedEvents).distinct()
  }

  /** The list of output events (the event topics this state machine generates). */
  val outputEvents: List<Event> by lazy {
    val localEvents =
      (vertexSet().flatMap { it.getActionsOfType<EventRaisingAction>() } +
          edgeSet().flatMap { it.getActionsOfType<EventRaisingAction>() })
        .flatMap { it.raises() }

    val nestedEvents = nested.flatMap { it.outputEvents }
    (localEvents + nestedEvents).distinct().filter { it.channel != EventChannel.INTERNAL }
  }

  /** The map of state names to state specifications. */
  private val states: Map<String, State> by lazy { vertexSet().associateBy { it.name } }

  init {
    description.states.forEach { (stateName, stateDesc) ->
      addVertex(State.create(csml, stateDesc, stateName).getOrThrow())
    }

    description.states.forEach { (sourceName, stateDesc) ->
      val source = getStateClassByName(sourceName)!!

      stateDesc.on.forEach { (event, transDesc) -> buildGraph(csml, source, event, transDesc) }
      stateDesc.always.forEach { transDesc -> buildGraph(csml, source, null, transDesc) }
    }
  }

  fun getStateClassByName(name: String) = states[name]

  private fun buildGraph(csml: Csml, source: State, event: String?, desc: TransitionDescription) {
    val target =
      desc.to?.let { targetName ->
        getStateClassByName(targetName)
          ?: error("state machine '${name}' has invalid transition target '$targetName'")
      } ?: source

    val transition = Transition.create(csml, desc, event).getOrThrow()

    require(addEdge(source, target, transition)) {
      "illegal transition in state machine '${name}' from '${source.name}'"
    }
  }

  companion object {
    fun create(
      csml: Csml,
      description: StateMachineDescription,
      name: String = "",
    ): Result<StateMachine> = runCatching {
      val nested =
        description.nested.map { (nestedName, smDesc) ->
          create(csml, smDesc, nestedName).getOrThrow()
        }

      StateMachine(csml, name, nested, description)
    }
  }
}
