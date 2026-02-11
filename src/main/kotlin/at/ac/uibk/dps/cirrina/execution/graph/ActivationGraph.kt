package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.StateMachine
import org.jgrapht.graph.DirectedMultigraph

class ActivationGraph :
  DirectedMultigraph<String, ActivationGraph.Activation>(Activation::class.java) {

  data class Activation(val source: String, val target: String, val event: Event)

  companion object {
    fun create(stateMachines: Collection<StateMachine>): ActivationGraph {
      val graph = ActivationGraph()

      stateMachines.forEach { graph.addVertex(it.name) }

      for (source in stateMachines) {
        val outputs = source.specification.outputEvents

        for (event in outputs) {
          for (target in stateMachines) {
            if (source.name == target.name) continue

            // TODO: This cannot be matched at this point because a raise action has an expression
            //  as target, unless we find a way to evaluate it here we cannot filter out targeted
            //  events here
            // if (event.target.isNotEmpty() && event.target != target.name) continue

            if (!target.specification.inputEvents.contains(event.topic)) continue

            val canDeliver =
              when (event.channel) {
                EventChannel.GLOBAL -> true
                EventChannel.EXTERNAL -> target.isSubscribedTo(source.name)
                else -> false
              }

            if (canDeliver) {
              graph.addEdge(source.name, target.name, Activation(source.name, target.name, event))
            }
          }
        }
      }
      return graph
    }
  }
}
