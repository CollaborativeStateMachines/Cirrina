package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.spec.Instance
import kotlin.time.Duration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeout
import org.jgrapht.graph.DirectedMultigraph

class EventGraph : DirectedMultigraph<String, EventGraph.Flow>(Flow::class.java) {

  private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  suspend fun await(instanceNames: Collection<String>, timeout: Duration): Boolean {
    val flows = getOutgoing(instanceNames.filter { containsVertex(it) })

    return try {
      withTimeout(timeout) {
        updates
          .onStart { emit(Unit) }
          .map { flows.all { it.isSubscribed } }
          .first { allSubscribed -> allSubscribed }
      }
      true
    } catch (_: TimeoutCancellationException) {
      false
    }
  }

  fun getIncoming(vertices: Collection<String>) = vertices.flatMap { incomingEdgesOf(it) }

  fun getOutgoing(vertices: Collection<String>) = vertices.flatMap { outgoingEdgesOf(it) }

  data class Flow(
    val source: String,
    val target: String,
    val event: Event,
    private val callback: () -> Unit,
  ) {
    @Volatile
    var isSubscribed: Boolean = false
      set(value) {
        if (field != value) {
          field = value
          callback()
        }
      }
  }

  companion object {
    fun create(instances: List<Instance>): EventGraph =
      EventGraph().apply {
        instances.forEach { addVertex(it.name) }

        instances
          .flatMap { source ->
            source.stateMachine.outputEvents.flatMap { event ->
              instances
                .filter { target ->
                  source.name != target.name && event.topic in target.stateMachine.inputEvents
                }
                .map { target -> Triple(source, target, event) }
            }
          }
          .forEach { (source, target, event) ->
            val flow =
              Flow(source.name, target.name, event.copy(source = source.name)) {
                this.updates.tryEmit(Unit)
              }

            addEdge(source.name, target.name, flow)
          }
      }
  }
}
