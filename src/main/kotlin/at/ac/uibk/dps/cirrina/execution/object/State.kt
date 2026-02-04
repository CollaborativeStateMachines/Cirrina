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
  @Assisted val spec: StateSpec,
  @Assisted private val parent: StateMachine,
  private val commandFactory: ActionCommandFactory,
) : Scope {
  private val entryActions = spec.entryActions.toTopologicalList()
  private val whileActions = spec.whileActions.toTopologicalList()
  private val exitActions = spec.exitActions.toTopologicalList()
  val timeoutActions = spec.afterActions.toTopologicalList().filterIsInstance<TimeoutAction>()

  override val extent: Extent by lazy {
    parent.extent.extend(Context.from(spec.staticContextDescription).getOrThrow())
  }

  fun getEntryActionCommands(ctx: CommandExecutionContext) =
    entryActions.map { commandFactory.create(it, ctx) }

  fun getWhileActionCommands(ctx: CommandExecutionContext) =
    whileActions.map { commandFactory.create(it, ctx) }

  fun getExitActionCommands(ctx: CommandExecutionContext) =
    exitActions.map { commandFactory.create(it, ctx) }

  private fun <V, E> Graph<V, E>.toTopologicalList(): List<V> =
    TopologicalOrderIterator(this).asSequence().toList()

  @AssistedFactory
  interface Factory {
    fun create(spec: StateSpec, parent: StateMachine): State
  }
}
