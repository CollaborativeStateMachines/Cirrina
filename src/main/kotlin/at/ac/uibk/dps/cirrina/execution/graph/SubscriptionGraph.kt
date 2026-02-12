package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.graph.ActivationGraph
import org.jgrapht.graph.DirectedMultigraph

class SubscriptionGraph : DirectedMultigraph<String, SubscriptionGraph.Flow>(Flow::class.java) {
  fun getOutwardEdges(vertices: List<String>): Set<Flow> =
    vertices.flatMap(::outgoingEdgesOf).toSet()

  fun getInwardEdges(vertices: List<String>): Set<Flow> =
    vertices.flatMap(::incomingEdgesOf).toSet()

  data class Flow(
    val source: String,
    val target: String,
    val channel: EventChannel,
    val topic: String,
    val isSubscribed: Boolean,
  ) {
    override fun toString() =
      "Flow(source='$source', target='$target', channel='$channel', topic='$topic', isSubscribed='$isSubscribed')"
  }

  companion object {
    fun create(activationGraph: ActivationGraph) =
      SubscriptionGraph().apply {
        activationGraph.vertexSet().forEach { addVertex(it) }

        activationGraph.edgeSet().forEach { activation ->
          val connection =
            Flow(
              source = activation.source,
              target = activation.target,
              channel = activation.channel,
              topic = activation.topic,
              isSubscribed = false,
            )
          addEdge(activation.source, activation.target, connection)
        }
      }
  }
}
