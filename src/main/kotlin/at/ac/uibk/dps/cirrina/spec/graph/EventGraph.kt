package at.ac.uibk.dps.cirrina.spec.graph

import at.ac.uibk.dps.cirrina.spec.Event
import at.ac.uibk.dps.cirrina.spec.Instance
import org.jgrapht.graph.DirectedMultigraph

class EventGraph : DirectedMultigraph<String, EventGraph.Flow>(Flow::class.java) {
  fun getOutgoing(vertices: Collection<String>) = vertices.flatMap { outgoingEdgesOf(it) }

  data class Flow(val source: String, val target: String, val event: Event)

  companion object {
    fun create(instances: List<Instance>): EventGraph =
      EventGraph().apply {
        instances.forEach { addVertex(it.name) }

        instances
          .flatMap { source ->
            source.stateMachine.outputEvents.flatMap { event ->
              instances
                .filter { target ->
                  source.name != target.name && event.topic in target.stateMachine.inputEvents
                }
                .map { target -> Triple(source, target, event) }
            }
          }
          .forEach { (source, target, event) ->
            addEdge(
              source.name,
              target.name,
              Flow(source.name, target.name, event.copy(source = source.name)),
            )
          }
      }
  }
}
