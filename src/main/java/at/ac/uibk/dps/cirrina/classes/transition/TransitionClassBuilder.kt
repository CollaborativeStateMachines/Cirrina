package at.ac.uibk.dps.cirrina.classes.transition

import at.ac.uibk.dps.cirrina.csm.Csml.TransitionDescription
import at.ac.uibk.dps.cirrina.execution.`object`.action.ActionBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.guard.GuardBuilder

/** [TransitionClass] builder. Builds a [TransitionClass] based on a [TransitionDescription]. */
class TransitionClassBuilder
private constructor(private val transitionDescription: TransitionDescription) {

  companion object {
    /**
     * Construct a builder from a transition description.
     *
     * @param transitionDescription transition description.
     * @return a new builder instance.
     */
    fun from(transitionDescription: TransitionDescription): TransitionClassBuilder =
      TransitionClassBuilder(transitionDescription)
  }

  private var event: String? = null

  /**
   * Sets the name of the event for an on-transition.
   *
   * @param event the event name.
   * @return this builder.
   */
  fun withEvent(event: String): TransitionClassBuilder {
    this.event = event
    return this
  }

  /**
   * Builds and returns a [TransitionClass].
   *
   * @return the fully constructed transition class.
   */
  fun build(): Result<TransitionClass> = runCatching {
    val to = transitionDescription.to

    // Resolves action descriptions into action objects
    val `do` =
      transitionDescription.`do`.map { description ->
        ActionBuilder.from(description).build().getOrThrow()
      }

    // Resolves guard expressions into guard objects
    val iif = transitionDescription.iif?.let { GuardBuilder.from(it).build().getOrThrow() }

    val or = transitionDescription.or

    TransitionClass(TransitionClass.Parameters(to, `do`, iif, or, event))
  }
}
