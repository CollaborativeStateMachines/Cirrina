package at.ac.uibk.dps.cirrina.spec.graph

import at.ac.uibk.dps.cirrina.execution.`object`.StateMachine
import at.ac.uibk.dps.cirrina.spec.Event
import java.util.concurrent.ConcurrentHashMap
import org.jgrapht.graph.DirectedMultigraph

class EventGraph : DirectedMultigraph<String, EventGraph.Flow>(Flow::class.java) {
  private val stateMachines = ConcurrentHashMap<String, StateMachine>()

  data class Flow(val source: String, val target: String, val event: Event)

  fun addInstance(stateMachine: StateMachine) {
    val name = stateMachine.name
    if (stateMachines.containsKey(name)) return

    addVertex(name)
    stateMachines[name] = stateMachine

    val newInputs = stateMachine.spec.inputEvents
    val newOutputs = stateMachine.spec.outputEvents

    stateMachines.values.forEach { existing ->
      if (existing.name == name) return@forEach

      newOutputs.forEach { event ->
        if (event.topic in existing.spec.inputEvents) {
          addEdge(name, existing.name, Flow(name, existing.name, event.copy(source = name)))
        }
      }

      existing.spec.outputEvents.forEach { event ->
        if (event.topic in newInputs) {
          addEdge(
            existing.name,
            name,
            Flow(existing.name, name, event.copy(source = existing.name)),
          )
        }
      }
    }
  }

  fun getOutgoing(vertices: Collection<String>) = vertices.flatMap { outgoingEdgesOf(it) }
}
