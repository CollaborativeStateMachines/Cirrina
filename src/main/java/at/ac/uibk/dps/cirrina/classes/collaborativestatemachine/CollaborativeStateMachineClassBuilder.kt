package at.ac.uibk.dps.cirrina.classes.collaborativestatemachine

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClassBuilder
import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import java.io.IOException

/**
 * Collaborative state machine builder, responsible for constructing a
 * [CollaborativeStateMachineClass] based on a [Csml] description.
 *
 * TODO: Add unit test for built graph.
 */
class CollaborativeStateMachineClassBuilder private constructor(private val csml: Csml) {

  companion object {
    /**
     * Construct a collaborative state machine builder from a description.
     *
     * @param csml collaborative state machine description.
     * @return a new builder instance.
     */
    @JvmStatic
    fun from(csml: Csml): CollaborativeStateMachineClassBuilder =
      CollaborativeStateMachineClassBuilder(csml)
  }

  /**
   * Builds and returns a [CollaborativeStateMachineClass].
   *
   * @return the fully constructed collaborative state machine class.
   * @throws IllegalStateException if the context or state machine components cannot be initialized.
   */
  fun build(): CollaborativeStateMachineClass {
    val persistentContext =
      try {
        ContextBuilder.from(csml.persistent).inMemoryContext(true).build()
      } catch (e: IOException) {
        throw IllegalStateException("Failed to build persistent context", e)
      }

    return try {
      CollaborativeStateMachineClass(persistentContext?.all ?: emptyList()).apply {
        buildVertices(this)
        buildEdges(this)
      }
    } catch (e: IOException) {
      throw IllegalStateException("Failed to initialize collaborative state machine", e)
    }
  }

  // Maps the nested state machine definitions into vertices for the collaborative graph
  private fun buildVertices(collaborativeStateMachineClass: CollaborativeStateMachineClass) {
    csml.stateMachines.entries
      .map { (name, description) ->
        StateMachineClassBuilder.from(description).withName(name).build()
      }
      .forEach { collaborativeStateMachineClass.addVertex(it) }
  }

  // Orchestrates the connection logic between different state machine vertices
  private fun buildEdges(collaborativeStateMachineClass: CollaborativeStateMachineClass) {
    val vertices = collaborativeStateMachineClass.vertexSet()

    // Apply the mapping to the graph
    vertices
      .flatMap { source -> source.outputEvents.map { event -> source to event } }
      .associateWith { (source, event) -> findTargetStateMachines(source, event, vertices) }
      .forEach { (sourceEventPair, targets) ->
        val (source, event) = sourceEventPair
        targets.forEach { target -> collaborativeStateMachineClass.addEdge(source, target, event) }
      }
  }

  // Filter logic to determine which state machines react to a specific raised event
  private fun findTargetStateMachines(
    source: StateMachineClass,
    raisedEvent: Event,
    allVertices: Set<StateMachineClass>,
  ): List<StateMachineClass> {
    return allVertices.filter { target ->
      val handlesEvent = target.inputEvents.contains(raisedEvent.name)
      val isChannelValid =
        when (raisedEvent.channel) {
          EventChannel.INTERNAL -> source == target
          EventChannel.GLOBAL,
          EventChannel.EXTERNAL -> true
          else -> false
        }
      handlesEvent && isChannelValid
    }
  }
}
