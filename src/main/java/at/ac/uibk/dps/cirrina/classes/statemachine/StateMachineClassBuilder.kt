package at.ac.uibk.dps.cirrina.classes.statemachine

import at.ac.uibk.dps.cirrina.classes.state.StateClassBuilder
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClassBuilder
import at.ac.uibk.dps.cirrina.csm.Csml.StateMachineDescription
import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription

/**
 * [StateMachineClass] builder. Builds a [StateMachineClass] based on a [StateMachineDescription].
 */
class StateMachineClassBuilder
private constructor(private val stateMachineDescription: StateMachineDescription) {

  companion object {
    /**
     * Construct a builder from a state machine description.
     *
     * @param stateMachineDescription state machine description.
     * @return this builder.
     */
    fun from(stateMachineDescription: StateMachineDescription): StateMachineClassBuilder =
      StateMachineClassBuilder(stateMachineDescription)
  }

  private var name: String? = null

  /**
   * Sets the name of the state machine.
   *
   * @param name name of the state machine.
   * @return this builder.
   */
  fun withName(name: String): StateMachineClassBuilder {
    this.name = name
    return this
  }

  private fun buildNestedStateMachines(): List<StateMachineClass> =
    stateMachineDescription.stateMachines.entries.map { (nestedName, description) ->
      from(description).withName(nestedName).build().getOrThrow()
    }

  private fun buildBase(): StateMachineClass {
    val nestedStateMachines = buildNestedStateMachines()

    val parameters =
      StateMachineClass.Parameters(
        name = name ?: "",
        transientContextDescription = stateMachineDescription.transient,
        nestedStateMachineClasses = nestedStateMachines,
      )

    val stateMachine = StateMachineClass(parameters)

    stateMachineDescription.states.forEach { (stateName, description) ->
      val state = StateClassBuilder.from(description).withName(stateName).build().getOrThrow()
      stateMachine.addVertex(state)
    }

    return stateMachine
  }

  /**
   * Builds and returns a [StateMachineClass].
   *
   * @return the fully constructed state machine class.
   */
  fun build(): Result<StateMachineClass> = runCatching {
    val stateMachine = buildBase()

    stateMachineDescription.states.forEach { (sourceName, stateDesc) ->
      // The source state is guaranteed to exist as it was added during buildBase
      val sourceStateClass = stateMachine.findStateClassByName(sourceName)!!

      fun processTransitionEntry(event: String?, description: TransitionDescription) {
        val targetStateClass =
          description.to?.let { targetName ->
            stateMachine.findStateClassByName(targetName)
              ?: throw IllegalArgumentException(
                "transition '$name' has an invalid target state '$targetName'."
              )
          } ?: sourceStateClass

        val builder = TransitionClassBuilder.from(description)
        event?.let { builder.withEvent(it) }

        if (
          !stateMachine.addEdge(sourceStateClass, targetStateClass, builder.build().getOrThrow())
        ) {
          throw IllegalArgumentException(
            "the edge '$name' between '${sourceStateClass.name}' and '${targetStateClass.name}' is illegal."
          )
        }
      }

      stateDesc.on.forEach { (event, desc) -> processTransitionEntry(event, desc) }
      stateDesc.always.forEach { desc -> processTransitionEntry(null, desc) }
    }

    stateMachine
  }
}
