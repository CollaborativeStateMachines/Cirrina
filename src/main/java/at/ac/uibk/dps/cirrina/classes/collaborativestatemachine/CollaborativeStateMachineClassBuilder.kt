package at.ac.uibk.dps.cirrina.classes.collaborativestatemachine

import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClassBuilder
import at.ac.uibk.dps.cirrina.csm.Csml.CollaborativeStateMachineDescription
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

/**
 * A builder for constructing [CollaborativeStateMachineClass] instances from
 * [CollaborativeStateMachineDescription] definitions.
 */
class CollaborativeStateMachineClassBuilder
private constructor(
  private val collaborativeStateMachineDescription: CollaborativeStateMachineDescription
) {

  companion object {
    /**
     * Creates a new [CollaborativeStateMachineClassBuilder] from the provided
     * [CollaborativeStateMachineDescription].
     *
     * @param collaborativeStateMachineDescription the collaborative state machine description.
     * @return a new builder instance.
     */
    fun from(
      collaborativeStateMachineDescription: CollaborativeStateMachineDescription
    ): CollaborativeStateMachineClassBuilder =
      CollaborativeStateMachineClassBuilder(collaborativeStateMachineDescription)
  }

  /**
   * Builds a [CollaborativeStateMachineClass].
   *
   * @return a [Result] containing the fully constructed collaborative machine class or a failure.
   */
  fun build(): Result<CollaborativeStateMachineClass> = runCatching {
    CollaborativeStateMachineClass(
        ContextBuilder.from(collaborativeStateMachineDescription.persistent)
          .inMemoryContext()
          .build()
          .getOrThrow()
          .getAll()
      )
      .apply {
        collaborativeStateMachineDescription.stateMachines.forEach { (name, desc) ->
          addVertex(StateMachineClassBuilder.from(desc).withName(name).build().getOrThrow())
        }
      }
      .also { graph -> buildEdges(graph) }
  }

  private fun buildEdges(graph: CollaborativeStateMachineClass) =
    graph.vertexSet().let { allStateMachines ->
      allStateMachines.forEach { source ->
        source.outputEvents.forEach { event ->
          findTargets(source, event, allStateMachines).forEach { target ->
            graph.addEdge(source, target, event)
          }
        }
      }
    }

  private fun findTargets(
    source: StateMachineClass,
    raisedEvent: Event,
    allStateMachines: Set<StateMachineClass>,
  ): List<StateMachineClass> =
    allStateMachines.filter { target ->
      target.inputEvents.contains(raisedEvent.name) &&
        when (raisedEvent.channel) {
          EventChannel.INTERNAL -> source == target
          EventChannel.GLOBAL,
          EventChannel.EXTERNAL -> true
          else -> false
        }
    }
}
