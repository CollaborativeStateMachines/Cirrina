package at.ac.uibk.dps.cirrina.execution.`object`.action

import com.google.common.collect.Iterables

/**
 * A builder that constructs an [ActionGraph] by sequentially linking a list of [Action]s.
 *
 * This builder creates a linear execution path. If extending an existing graph, the first new
 * action is linked to the last vertex of the provided graph.
 *
 * @property actions the list of actions to be added to the graph.
 * @property actionGraph the base graph to extend, or a new empty graph by default.
 */
class ActionGraphBuilder
private constructor(
  private val actions: List<Action>,
  private val actionGraph: ActionGraph = ActionGraph(),
) {

  companion object {
    fun from(actions: List<Action>): ActionGraphBuilder = ActionGraphBuilder(actions)
  }

  /**
   * Builds the [ActionGraph].
   *
   * @return the populated [ActionGraph].
   */
  fun build(): ActionGraph =
    actionGraph.apply {
      actions.fold(initialPrevious()) { prev, current ->
        addVertex(current)
        prev?.let { addEdge(it, current) }
        current
      }
    }

  private fun initialPrevious(): Action? =
    if (actionGraph.vertexSet().isEmpty()) null else Iterables.getLast(actionGraph.vertexSet())
}
