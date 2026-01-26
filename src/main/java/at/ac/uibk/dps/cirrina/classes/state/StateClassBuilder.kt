package at.ac.uibk.dps.cirrina.classes.state

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription
import at.ac.uibk.dps.cirrina.csm.Csml.StateDescription
import at.ac.uibk.dps.cirrina.execution.`object`.action.Action
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.action.TimeoutAction

/**
 * A builder for constructing [StateClass]s from [StateDescription]s.
 *
 * @property stateDescription the source containing the state's structural definition.
 * @property name an optional identifier for the state, required to be provided.
 */
class StateClassBuilder
private constructor(
  private val stateDescription: StateDescription,
  private val name: String? = null,
) {

  companion object {
    /** Creates a new [StateClassBuilder] from the provided [stateDescription]. */
    fun from(stateDescription: StateDescription): StateClassBuilder =
      StateClassBuilder(stateDescription)
  }

  /** Associates a [name] with the state and returns a new builder instance. */
  fun withName(name: String): StateClassBuilder = StateClassBuilder(stateDescription, name)

  /**
   * Builds a [StateClass].
   *
   * @return a [Result] containing the built [StateClass] or a failure if action resolution or
   *   validation fails.
   */
  fun build(): Result<StateClass> = runCatching {
    StateClass(
      name.orEmpty(),
      stateDescription.isInitial,
      stateDescription.isTerminal,
      stateDescription.static,
      resolveActions(stateDescription.entry),
      resolveActions(stateDescription.exit),
      resolveActions(stateDescription.`while`),
      resolveAfterActions(stateDescription.after),
    )
  }

  private fun resolveActions(descriptions: List<ActionDescription>): List<Action> =
    descriptions.map { ActionBuilder.from(it).build().getOrThrow() }

  private fun resolveAfterActions(descriptions: Map<String, ActionDescription>): List<Action> =
    descriptions
      .map { (name, desc) -> ActionBuilder.from(desc).withName(name).build().getOrThrow() }
      .also { actions ->
        require(actions.all { it is TimeoutAction }) {
          "all 'after' actions must be timeout actions"
        }
      }
}
