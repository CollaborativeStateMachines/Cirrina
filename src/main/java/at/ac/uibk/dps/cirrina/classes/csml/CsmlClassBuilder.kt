package at.ac.uibk.dps.cirrina.classes.collaborativestatemachine

import at.ac.uibk.dps.cirrina.classes.csml.CsmlClass
import at.ac.uibk.dps.cirrina.csm.Csml

/** A builder for constructing [CsmlClass] instances from [Csml] descriptions. */
class CsmlClassBuilder private constructor(private val csmlDescription: Csml) {

  companion object {
    /**
     * Creates a new [CsmlClassBuilder] from the provided [Csml] description.
     *
     * @param csml the csml description.
     * @return a new builder instance.
     */
    fun from(csml: Csml): CsmlClassBuilder = CsmlClassBuilder(csml)
  }

  /**
   * Builds a [CsmlClass].
   *
   * @return a [Result] containing the fully constructed csml class or a failure.
   */
  fun build(): Result<CsmlClass> = runCatching {
    CsmlClass(
      CollaborativeStateMachineClassBuilder.from(csmlDescription.collaborativeStateMachine)
        .build()
        .getOrThrow(),
      csmlDescription.instantiate,
    )
  }
}
