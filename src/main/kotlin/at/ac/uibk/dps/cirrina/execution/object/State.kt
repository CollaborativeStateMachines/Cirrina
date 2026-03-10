package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.spec.State as StateSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.jgrapht.Graph
import org.jgrapht.traverse.TopologicalOrderIterator

class State
@AssistedInject
internal constructor(
  @Assisted val specification: StateSpec,
  @Assisted private val parent: StateMachine,
) : Scope {
  val entryActions = specification.entry.toTopologicalList()
  val duringActions = specification.during.toTopologicalList()
  val exitActions = specification.exit.toTopologicalList()
  val timeout = specification.after.toTopologicalList().filterIsInstance<TimeoutAction>()

  override val extent: Extent by lazy { parent.extent.extend(Context.from(specification.static)) }

  private fun <V, E> Graph<V, E>.toTopologicalList(): List<V> =
    TopologicalOrderIterator(this).asSequence().toList()

  @AssistedFactory
  interface Factory {
    fun create(spec: StateSpec, parent: StateMachine): State
  }
}
