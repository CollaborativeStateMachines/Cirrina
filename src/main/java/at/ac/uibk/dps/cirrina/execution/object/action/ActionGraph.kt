package at.ac.uibk.dps.cirrina.execution.`object`.action

import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph

/**
 * Represents a directed graph of [Action] objects where edges define the sequence of execution.
 *
 * The direction of edges indicates that the source action must be processed before the target
 * action.
 */
class ActionGraph : SimpleDirectedGraph<Action, DefaultEdge> {

  /** Initializes an empty action graph. */
  constructor() : super(DefaultEdge::class.java)

  /**
   * Filters and returns actions of a specific type [T].
   *
   * @return a list of actions matching type [T].
   */
  inline fun <reified T> getActionsOfType(): List<T> = vertexSet().filterIsInstance<T>()
}
