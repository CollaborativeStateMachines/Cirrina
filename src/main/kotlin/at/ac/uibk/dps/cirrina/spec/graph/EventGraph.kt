package at.ac.uibk.dps.cirrina.spec.graph

import at.ac.uibk.dps.cirrina.execution.`object`.StateMachine
import at.ac.uibk.dps.cirrina.spec.Event
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.jgrapht.graph.DirectedMultigraph

class EventGraph {
  private val delegate = DirectedMultigraph<StateMachine, Flow>(Flow::class.java)

  private val lock = ReentrantReadWriteLock()

  val instances: List<StateMachine>
    get() = lock.read { delegate.vertexSet().toList() }

  data class Flow(val source: String, val target: String, val event: Event)

  fun addInstance(stateMachine: StateMachine) {
    lock.write {
      if (delegate.vertexSet().any { it.name == stateMachine.name }) return

      delegate.addVertex(stateMachine)

      val newInputs = stateMachine.specification.inputEvents
      val newOutputs = stateMachine.specification.outputEvents

      delegate.vertexSet().forEach { existing ->
        if (existing.name == stateMachine.name) return@forEach

        newOutputs.forEach { event ->
          if (event.topic in existing.specification.inputEvents) {
            delegate.addEdge(
              stateMachine,
              existing,
              Flow(stateMachine.name, existing.name, event.copy(source = stateMachine.name)),
            )
          }
        }

        existing.specification.outputEvents.forEach { event ->
          if (event.topic in newInputs) {
            delegate.addEdge(
              existing,
              stateMachine,
              Flow(existing.name, stateMachine.name, event.copy(source = existing.name)),
            )
          }
        }
      }
    }
  }

  fun getOutgoing(vertices: List<StateMachine>): List<Flow> {
    return lock.read { vertices.flatMap { delegate.outgoingEdgesOf(it) } }
  }
}
