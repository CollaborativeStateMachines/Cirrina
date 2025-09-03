package at.ac.uibk.dps.cirrina.execution.object.action;

import java.util.List;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

/**
 * Abstract action graph, contains actions as vertices and edges indicating the order of execution.
 * <p>
 * Formally, an action graph is a directed graph \(\mathcal A = (V, A)\) with:
 * <ul>
 *   <li><i>\(V\)</i>: the set of action objects;</li>
 *   <li><i>\(A\)</i>: the set of ordered pairs of action objects.</li>
 * </ul>
 * <p>
 * The order direction of actions contained within an action graph represents the order of execution of the action objects contained in it.
 */
public final class ActionGraph extends SimpleDirectedGraph<Action, DefaultEdge> {

  /**
   * Initializes an empty action graph.
   */
  ActionGraph() {
    super(DefaultEdge.class);
  }

  /**
   * Initializes an empty action graph and copies the vertices and edges of another action graph.
   *
   * @param actionGraph The action graph to copy.
   */
  public ActionGraph(ActionGraph actionGraph) {
    this();
    // Add vertices
    actionGraph.vertexSet().forEach(this::addVertex);

    // Add edges
    actionGraph
      .edgeSet()
      .forEach(edge ->
        addEdge(actionGraph.getEdgeSource(edge), actionGraph.getEdgeTarget(edge), edge)
      );
  }

  /**
   * Returns the actions containing in this action graph that have a certain type. The order of actions is maintained.
   *
   * @param type Type of actions to return.
   * @param <T>  Type.
   * @return Actions of type.
   */
  public <T> List<T> getActionsOfType(Class<T> type) {
    return vertexSet().stream().filter(type::isInstance).map(type::cast).toList();
  }

  public List<Action> getActions() {
    return vertexSet().stream().toList();
  }
}
