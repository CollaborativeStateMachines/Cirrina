package at.ac.uibk.dps.cirrina.classes.state

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction

/** [StateClass] builder. Builds a [StateClass] based on a [StateDescription]. */
class StateClassBuilder private constructor(private val stateDescription: StateDescription) {

  companion object {
    /**
     * Construct a state class builder from a state description.
     *
     * @param stateDescription state description.
     * @return state class builder.
     */
    fun from(stateDescription: StateDescription): StateClassBuilder =
      StateClassBuilder(stateDescription)
  }

  private var name: String? = null

  /**
   * Sets the name of the state.
   *
   * @param name state name.
   * @return this builder instance.
   */
  fun withName(name: String): StateClassBuilder {
    this.name = name
    return this
  }

  /**
   * Builds and returns a [StateClass].
   *
   * @return the fully constructed state class.
   */
  fun build(): Result<StateClass> = runCatching {
    fun resolveAction(desc: ActionDescription, actionName: String? = null): Action {
      val builder = ActionBuilder.from(desc)
      actionName?.takeUnless { it.isBlank() }?.let { builder.withName(it) }
      return builder.build().getOrThrow()
    }

    val entryActions = stateDescription.entry.map { resolveAction(it) }
    val exitActions = stateDescription.exit.map { resolveAction(it) }
    val whileActions = stateDescription.`while`.map { resolveAction(it) }
    val afterActions = stateDescription.after.map { (name, desc) -> resolveAction(desc, name) }

    // Validates that all after actions are specifically timeout actions
    require(afterActions.all { it is TimeoutAction }) { "after action is not a timeout action." }

    StateClass(
      StateClass.Parameters(
        name ?: "",
        stateDescription.isInitial,
        stateDescription.isTerminal,
        entryActions,
        exitActions,
        whileActions,
        afterActions,
        stateDescription.static,
      )
    )
  }
}
