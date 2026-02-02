package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.CollaborativeStateMachineDescription
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import org.jgrapht.graph.DirectedPseudograph

class CollaborativeStateMachine
private constructor(val persistentContextVariables: List<ContextVariable>) :
  DirectedPseudograph<StateMachine, Event>(Event::class.java) {

  fun findStateMachineClassByName(name: String): StateMachine? {
    return vertexSet().singleOrNull { it.name == name }
  }

  companion object {
    fun create(
      description: CollaborativeStateMachineDescription
    ): Result<CollaborativeStateMachine> = runCatching {
      val variables =
        ContextBuilder.from(description.persistent).inMemoryContext().build().getOrThrow().getAll()

      val spec = CollaborativeStateMachine(variables)

      description.stateMachines.forEach { (name, desc) ->
        val stateMachineSpec = StateMachine.create(desc, name).getOrThrow()
        spec.addVertex(stateMachineSpec)
      }

      buildGraph(spec)

      spec
    }

    private fun buildGraph(graph: CollaborativeStateMachine) {
      val allNodes = graph.vertexSet()

      for (source in allNodes) {
        for (event in source.outputEvents) {
          allNodes
            .filter { target ->
              target.inputEvents.contains(event.topic) &&
                when (event.channel) {
                  EventChannel.INTERNAL -> source == target
                  EventChannel.GLOBAL,
                  EventChannel.EXTERNAL -> true
                  else -> false
                }
            }
            .forEach { target -> graph.addEdge(source, target, event) }
        }
      }
    }
  }
}
