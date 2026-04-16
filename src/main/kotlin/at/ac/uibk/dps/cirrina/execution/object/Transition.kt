package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.spec.Action
import at.ac.uibk.dps.cirrina.spec.Transition as TransitionSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.jgrapht.traverse.TopologicalOrderIterator

class Transition @AssistedInject internal constructor(@Assisted val specification: TransitionSpec) {
  val actions: List<Action> = TopologicalOrderIterator(specification.actions).asSequence().toList()

  val isInternal: Boolean
    get() = specification.to == null

  fun targetStateName(isOr: Boolean): String? = if (isOr) specification.or else specification.to

  override fun toString(): String =
    "${this::class.simpleName}(internal='$isInternal', target='${specification.to}', or='${specification.or}')"

  @AssistedFactory
  interface Factory {
    fun create(specification: TransitionSpec): Transition
  }
}
