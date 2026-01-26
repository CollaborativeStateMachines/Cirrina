package at.ac.uibk.dps.cirrina.classes.statemachine

import at.ac.uibk.dps.cirrina.classes.state.StateClass
import at.ac.uibk.dps.cirrina.classes.state.StateClassBuilder
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClassBuilder
import at.ac.uibk.dps.cirrina.csm.Csml.StateMachineDescription
import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription

/**
 * A builder for constructing [StateMachineClass]s.
 *
 * This builder leverages deep chaining to resolve nested state machines, state vertices, and
 * transition edges in a single execution pipeline.
 */
class StateMachineClassBuilder
private constructor(
  private val stateMachineDescription: StateMachineDescription,
  private val name: String? = null,
) {

  companion object {
    /** Starts a builder from the provided description. */
    fun from(stateMachineDescription: StateMachineDescription): StateMachineClassBuilder =
      StateMachineClassBuilder(stateMachineDescription)
  }

  /** Creates a new builder instance with the assigned name. */
  fun withName(name: String): StateMachineClassBuilder =
    StateMachineClassBuilder(stateMachineDescription, name)

  /**
   * Builds a [StateMachineClass].
   *
   * @return a [Result] containing the built state machine or the first encountered failure.
   */
  fun build(): Result<StateMachineClass> = runCatching {
    StateMachineClass(
        name.orEmpty(),
        stateMachineDescription.stateMachines.entries.map { (nestedName, desc) ->
          from(desc).withName(nestedName).build().getOrThrow()
        },
        stateMachineDescription.transient,
      )
      .apply {
        stateMachineDescription.states.forEach { (stateName, desc) ->
          addVertex(StateClassBuilder.from(desc).withName(stateName).build().getOrThrow())
        }
      }
      .also { stateMachine ->
        stateMachineDescription.states.forEach { (sourceName, stateDesc) ->
          val source = stateMachine.getStateClassByName(sourceName)!!

          stateDesc.on.forEach { (event, desc) -> addTransition(stateMachine, source, event, desc) }
          stateDesc.always.forEach { desc -> addTransition(stateMachine, source, null, desc) }
        }
      }
  }

  private fun addTransition(
    graph: StateMachineClass,
    source: StateClass,
    event: String?,
    desc: TransitionDescription,
  ) =
    require(
      graph.addEdge(
        source,
        desc.to?.let { targetName ->
          graph.getStateClassByName(targetName)
            ?: throw IllegalArgumentException(
              "state machine '${graph.name}' has invalid transition target: $targetName"
            )
        } ?: source,
        TransitionClassBuilder.from(desc)
          .let { builder -> event?.let { builder.withEvent(it) } ?: builder }
          .build()
          .getOrThrow(),
      )
    ) {
      "illegal transition in state machine '${graph.name}' from '${source.name}'"
    }
}
