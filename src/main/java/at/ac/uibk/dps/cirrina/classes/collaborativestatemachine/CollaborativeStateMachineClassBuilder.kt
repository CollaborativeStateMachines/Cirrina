package at.ac.uibk.dps.cirrina.classes.collaborativestatemachine

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClassBuilder
import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

/**
 * [CollaborativeStateMachineClass] builder. Builds a [CollaborativeStateMachineClass] based on a
 * [Csml].
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
    fun from(csml: Csml): CollaborativeStateMachineClassBuilder =
      CollaborativeStateMachineClassBuilder(csml)
  }

  /**
   * Builds and returns a [CollaborativeStateMachineClass].
   *
   * @return the fully constructed collaborative state machine class.
   */
  fun build(): Result<CollaborativeStateMachineClass> =
    ContextBuilder.from(csml.persistent)
      .inMemoryContext(true)
      .build() // Returns Result<Context>
      .map { context ->
        // Use getAll() which also returns a Result
        val variables = context.getAll().getOrDefault(emptyList())

        CollaborativeStateMachineClass(variables).apply {
          buildVertices(this)
          buildEdges(this)
        }
      }

  private fun buildVertices(collaborativeStateMachineClass: CollaborativeStateMachineClass) {
    csml.stateMachines.entries
      .map { (name, description) ->
        StateMachineClassBuilder.from(description).withName(name).build()
      }
      .forEach { collaborativeStateMachineClass.addVertex(it.getOrThrow()) }
  }

  private fun buildEdges(collaborativeStateMachineClass: CollaborativeStateMachineClass) {
    val vertices = collaborativeStateMachineClass.vertexSet()

    vertices
      .flatMap { source -> source.outputEvents.map { event -> source to event } }
      .associateWith { (source, event) ->
        findTargetForRaisedEventStateMachines(source, event, vertices)
      }
      .forEach { (sourceEventPair, targets) ->
        val (source, event) = sourceEventPair
        targets.forEach { target -> collaborativeStateMachineClass.addEdge(source, target, event) }
      }
  }

  private fun findTargetForRaisedEventStateMachines(
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
