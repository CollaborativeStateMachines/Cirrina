package at.ac.uibk.dps.cirrina.graph

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.spec.Instance
import org.jgrapht.graph.DirectedMultigraph

class ActivationGraph :
  DirectedMultigraph<String, ActivationGraph.Activation>(Activation::class.java) {
  data class Activation(
    val source: String,
    val target: String,
    val channel: EventChannel,
    val topic: String,
  ) {
    override fun toString() =
      "Activation(source='$source', target='$target', channel='$channel', topic='$topic')"
  }

  companion object {
    fun create(declared: Collection<Instance>): ActivationGraph {
      return ActivationGraph().apply {
        declared.forEach { addVertex(it.name) }

        for (source in declared) {
          val outputs = source.stateMachine.outputEvents

          for (event in outputs) {
            for (target in declared) {
              if (source.name == target.name) continue

              // TODO: This cannot be matched at this point because a raise action has an expression
              //  as target, unless we find a way to evaluate it here we cannot filter out targeted
              //  events here
              // if (event.target.isNotEmpty() && event.target != target.name) continue

              if (!target.stateMachine.inputEvents.contains(event.topic)) continue

              val canDeliver =
                when (event.channel) {
                  EventChannel.GLOBAL -> true
                  EventChannel.EXTERNAL -> target.isSubscribedTo(source.name)
                  else -> false
                }

              if (canDeliver) {
                addEdge(
                  source.name,
                  target.name,
                  Activation(source.name, target.name, event.channel, event.topic),
                )
              }
            }
          }
        }
      }
    }
  }
}
