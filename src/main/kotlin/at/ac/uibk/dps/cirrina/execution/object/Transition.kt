package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.spec.Transition as TransitionSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.jgrapht.traverse.TopologicalOrderIterator

class Transition
@AssistedInject
internal constructor(
  @Assisted private val spec: TransitionSpec,
  @Assisted val isOr: Boolean,
  private val commandFactory: ActionCommandFactory,
) {
  private val sortedActions: List<Action> =
    TopologicalOrderIterator(spec.actions).asSequence().toList()

  init {
    require(!isOr || spec.or != null) { "or transition must have a valid 'or' target state" }
  }

  val isInternal: Boolean
    get() = spec.to == null

  val targetStateName: String?
    get() = if (isOr) spec.or else spec.to

  fun getActionCommands(ctx: CommandExecutionContext): List<ActionCommand> =
    sortedActions.map { commandFactory.create(it, ctx) }

  override fun toString(): String =
    "${this::class.simpleName}(internal='$isInternal', target='$targetStateName', or='$isOr')"

  @AssistedFactory
  interface Factory {
    fun create(spec: TransitionSpec, isOr: Boolean): Transition
  }
}
