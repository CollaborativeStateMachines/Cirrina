package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.execution.`object`.StateMachine
import org.jgrapht.graph.DirectedMultigraph

class ActivationGraph : DirectedMultigraph<String, String>(String::class.java) {

  companion object {
    fun create(stateMachines: Collection<StateMachine>): ActivationGraph {
      return ActivationGraph().apply {}
    }
  }
}
