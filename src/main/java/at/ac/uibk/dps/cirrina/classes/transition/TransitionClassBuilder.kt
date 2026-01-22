package at.ac.uibk.dps.cirrina.classes.transition

import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.guard.GuardBuilder

/**
 * A functional builder for constructing [TransitionClass] instances from [TransitionDescription]s.
 *
 * @property transitionDescription the source for the transition.
 * @property event the optional event name that triggers this transition.
 */
class TransitionClassBuilder
private constructor(
  private val transitionDescription: TransitionDescription,
  private val event: String? = null,
) {

  /**
   * Associates a specific [event] name with this transition and returns a new builder instance.
   *
   * @param event the identifier of the event to trigger this transition.
   * @return a new builder instance containing the specified event.
   */
  fun withEvent(event: String): TransitionClassBuilder =
    TransitionClassBuilder(transitionDescription, event)

  /**
   * Builds a [TransitionClass].
   *
   * @return a [Result] containing the fully constructed [TransitionClass].
   */
  fun build(): Result<TransitionClass> = runCatching {
    TransitionClass(
      event = event,
      to = transitionDescription.to,
      iif = transitionDescription.iif?.let { GuardBuilder.from(it).build().getOrThrow() },
      `do` = transitionDescription.`do`.map { ActionBuilder.from(it).build().getOrThrow() },
      or = transitionDescription.or,
    )
  }

  companion object {
    /**
     * Creates a starting [TransitionClassBuilder] from the provided [transitionDescription].
     *
     * @param transitionDescription the source description.
     * @return a new builder instance.
     */
    fun from(transitionDescription: TransitionDescription): TransitionClassBuilder =
      TransitionClassBuilder(transitionDescription)
  }
}
