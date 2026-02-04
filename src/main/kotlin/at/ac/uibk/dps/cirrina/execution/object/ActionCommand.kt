package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

data class CommandExecutionContext(
  val scope: Scope,
  val serviceImplementationSelector: ServiceImplementationSelector,
  val stateMachineEventHandler: StateMachine.StateMachineEventHandler,
  val coroutineScope: CoroutineScope,
  val raisingEvent: Event? = null,
  val isWhile: Boolean = false,
)

interface ActionCommandFactory {
  fun create(action: Action, commandExecutionContext: CommandExecutionContext): ActionCommand
}

class ActionCommandFactoryImpl(private val meterRegistry: MeterRegistry) : ActionCommandFactory {
  override fun create(
    action: Action,
    commandExecutionContext: CommandExecutionContext,
  ): ActionCommand =
    when (action) {
      is EvalAction -> ActionEvalCommand(action, commandExecutionContext, this, meterRegistry)
      is InvokeAction -> ActionInvokeCommand(action, commandExecutionContext, this, meterRegistry)
      is MatchAction -> ActionMatchCommand(action, commandExecutionContext, this, meterRegistry)
      is RaiseAction -> ActionRaiseCommand(action, commandExecutionContext, this, meterRegistry)
      is TimeoutAction -> ActionTimeoutCommand(action, commandExecutionContext, this, meterRegistry)
      is TimeoutResetAction ->
        ActionTimeoutResetCommand(action, commandExecutionContext, this, meterRegistry)
      else -> error("unknown action type: ${action::class.simpleName}")
    }
}

interface Scope {
  val extent: Extent
}

abstract class ActionCommand
internal constructor(
  protected val commandExecutionContext: CommandExecutionContext,
  protected val commandFactory: ActionCommandFactory,
  protected val meterRegistry: MeterRegistry,
) {
  abstract fun execute(): List<ActionCommand>
}

class ActionEvalCommand
internal constructor(
  private val evalAction: EvalAction,
  commandExecutionContext: CommandExecutionContext,
  commandFactory: ActionCommandFactory,
  meterRegistry: MeterRegistry,
) : ActionCommand(commandExecutionContext, commandFactory, meterRegistry) {
  override fun execute(): List<ActionCommand> =
    evalAction.expression.execute(commandExecutionContext.scope.extent).run { emptyList() }
}

class ActionInvokeCommand
internal constructor(
  private val invokeAction: InvokeAction,
  commandExecutionContext: CommandExecutionContext,
  commandFactory: ActionCommandFactory,
  meterRegistry: MeterRegistry,
) : ActionCommand(commandExecutionContext, commandFactory, meterRegistry) {
  private val logger = KotlinLogging.logger {}

  override fun execute(): List<ActionCommand> {
    val service = selectServiceImplementation()
    val input = prepareInput(commandExecutionContext.scope.extent)

    commandExecutionContext.coroutineScope.launch {
      runCatching { service.invoke(input) }
        .onSuccess { raiseEvents(it) }
        .onFailure { logger.error(it) { "service invocation failed" } }
    }

    return emptyList()
  }

  private fun selectServiceImplementation(): ServiceImplementation =
    commandExecutionContext.serviceImplementationSelector.select(
      invokeAction.type,
      invokeAction.mode,
    ) ?: error("no service implementation found for type '${invokeAction.type}'")

  private fun prepareInput(extent: Extent): List<ContextVariable> =
    invokeAction.input.map { it.evaluate(extent) }

  private fun raiseEvents(output: List<ContextVariable>) {
    invokeAction.raises.forEach { eventTemplate ->
      val event = eventTemplate.copy(data = output)
      val handler =
        when (event.channel) {
          EventChannel.INTERNAL ->
            commandExecutionContext.stateMachineEventHandler::propagateToParent
          else -> commandExecutionContext.stateMachineEventHandler::sendEvent
        }
      handler(event)
    }
  }
}

class ActionMatchCommand
internal constructor(
  private val matchAction: MatchAction,
  commandExecutionContext: CommandExecutionContext,
  commandFactory: ActionCommandFactory,
  meterRegistry: MeterRegistry,
) : ActionCommand(commandExecutionContext, commandFactory, meterRegistry) {
  override fun execute(): List<ActionCommand> {
    val extent = commandExecutionContext.scope.extent
    val matchValue = matchAction.value.execute(extent)

    val selectedActions =
      matchAction.cases.entries
        .filter { (expression, _) -> expression.execute(extent) == matchValue }
        .map { it.value }
        .ifEmpty { listOfNotNull(matchAction.default) }

    return selectedActions.map { commandFactory.create(it, commandExecutionContext) }
  }
}

class ActionRaiseCommand
internal constructor(
  private val raiseAction: RaiseAction,
  commandExecutionContext: CommandExecutionContext,
  commandFactory: ActionCommandFactory,
  meterRegistry: MeterRegistry,
) : ActionCommand(commandExecutionContext, commandFactory, meterRegistry) {
  override fun execute(): List<ActionCommand> {
    val event =
      raiseAction.event.evaluateData(commandExecutionContext.scope.extent).run {
        val target = raiseAction.target?.execute(commandExecutionContext.scope.extent) as? String
        if (target != null) copy(target = target) else this
      }

    with(commandExecutionContext.stateMachineEventHandler) {
      if (event.channel == EventChannel.INTERNAL) propagateToParent(event) else sendEvent(event)
    }

    return emptyList()
  }
}

class ActionTimeoutCommand
internal constructor(
  private val timeoutAction: TimeoutAction,
  commandExecutionContext: CommandExecutionContext,
  commandFactory: ActionCommandFactory,
  meterRegistry: MeterRegistry,
) : ActionCommand(commandExecutionContext, commandFactory, meterRegistry) {
  override fun execute(): List<ActionCommand> =
    listOf(commandFactory.create(timeoutAction.`do`, commandExecutionContext))
}

class ActionTimeoutResetCommand
internal constructor(
  val timeoutResetAction: TimeoutResetAction,
  commandExecutionContext: CommandExecutionContext,
  commandFactory: ActionCommandFactory,
  meterRegistry: MeterRegistry,
) : ActionCommand(commandExecutionContext, commandFactory, meterRegistry) {
  override fun execute(): List<ActionCommand> = emptyList()
}
