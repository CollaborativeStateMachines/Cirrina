package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.execution.`object`.Action
import kotlin.collections.forEach
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph

class ActionGraph private constructor() :
  SimpleDirectedGraph<Action, DefaultEdge>(DefaultEdge::class.java) {

  inline fun <reified T : Action> getActionsOfType(): List<T> = vertexSet().filterIsInstance<T>()

  companion object {
    fun create(actions: List<Action>, baseGraph: ActionGraph = ActionGraph()): ActionGraph =
      baseGraph.apply {
        var lastVertex = vertexSet().lastOrNull()

        actions.forEach { action ->
          addVertex(action)
          lastVertex?.let { addEdge(it, action) }
          lastVertex = action
        }
      }
  }
}
