package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import io.dropwizard.metrics5.MetricName
import io.dropwizard.metrics5.MetricRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

interface Scope {
  val extent: Extent
}

class ActionExecutor(
  private val selector: ServiceImplementationSelector,
  private val metricRegistry: MetricRegistry,
  private val eventHandler: StateMachine.StateMachineEventHandler,
  private val coroutineScope: CoroutineScope,
) {
  fun execute(action: Action, scope: Scope): List<Action> =
    when (action) {
      is EvalAction -> executeEval(action, scope)
      is InvokeAction -> executeInvoke(action, scope)
      is MatchAction -> executeMatch(action, scope)
      is EmitAction -> executeEmit(action, scope)
      is TimeoutAction -> listOf(action.triggers)
      is TimeoutResetAction -> emptyList()
      is LogAction -> executeLog(action, scope)
      is CtrAction -> executeCtr(action, scope)
      else -> error("unknown action type: ${action::class.simpleName}")
    }

  private fun executeEval(action: EvalAction, scope: Scope): List<Action> {
    action.expression.execute(scope.extent)
    return emptyList()
  }

  private fun executeInvoke(action: InvokeAction, scope: Scope): List<Action> {
    val service =
      selector.select(action.type, action.mode)
        ?: error("no service implementation found for type '${action.type}'")

    val input = action.input.map { it.evaluate(scope.extent) }

    coroutineScope.launch {
      runCatching { service.invoke(input) }
        .onSuccess { output ->
          action.emits.forEach { eventTemplate ->
            val emittedEvent = eventTemplate.copy(data = output)
            if (emittedEvent.channel == EventChannel.INTERNAL) {
              eventHandler.propagateToParent(emittedEvent)
            } else {
              eventHandler.emit(emittedEvent)
            }
          }
        }
        .onFailure { logger.error(it) { "service invocation failed" } }
    }

    return emptyList()
  }

  private fun executeMatch(action: MatchAction, scope: Scope): List<Action> =
    action.cases.entries
      .filter { (expression, _) -> expression.execute(scope.extent) == true }
      .flatMap { it.value }
      .ifEmpty { listOfNotNull(action.default) }

  private fun executeEmit(action: EmitAction, scope: Scope): List<Action> {
    val emittedEvent =
      action.event.evaluateData(scope.extent).run {
        val target = action.target?.execute(scope.extent) as? String
        if (target != null) copy(target = target) else this
      }

    with(eventHandler) {
      if (emittedEvent.channel == EventChannel.INTERNAL) propagateToParent(emittedEvent)
      else emit(emittedEvent)
    }

    return emptyList()
  }

  private fun executeLog(action: LogAction, scope: Scope): List<Action> {
    action.message.execute(scope.extent).toString().also { logger.info(it) }
    return emptyList()
  }

  private fun executeCtr(action: CtrAction, scope: Scope): List<Action> {
    val tags =
      action.tag.entries.associate { (key, value) -> key to value.execute(scope.extent).toString() }

    val name = MetricName.build(action.counter).tagged(tags)
    metricRegistry.counter(name).inc(action.by)

    return emptyList()
  }
}
