package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.spec.StateMachine
import kotlin.collections.forEach
import org.jgrapht.graph.DirectedMultigraph

class EventGraph : DirectedMultigraph<String, EventGraph.Flow>(Flow::class.java) {
  fun getIncoming(vertices: Collection<String>) = vertices.flatMap { v -> incomingEdgesOf(v) }

  fun getOutgoing(vertices: Collection<String>) = vertices.flatMap { v -> outgoingEdgesOf(v) }

  data class Flow(
    val source: String,
    val target: String,
    val channel: EventChannel,
    val topic: String,
    @Volatile var isSubscribed: Boolean = false,
  ) {
    override fun toString() =
      "Flow(source='$source', target='$target', channel='$channel', topic='$topic', isSubscribed='$isSubscribed')"
  }

  companion object {
    fun create(runtimeInstances: Map<String, StateMachine>): EventGraph {
      return EventGraph().apply {
        runtimeInstances.keys.forEach { addVertex(it) }

        for ((sourceName, sourceSpec) in runtimeInstances) {
          val outputs = sourceSpec.outputEvents

          for (event in outputs) {
            for ((targetName, targetSpec) in runtimeInstances) {
              if (sourceName == targetName) continue

              if (!targetSpec.inputEvents.contains(event.topic)) continue

              addEdge(
                sourceName,
                targetName,
                Flow(sourceName, targetName, event.channel, event.topic),
              )
            }
          }
        }
      }
    }
  }
}
