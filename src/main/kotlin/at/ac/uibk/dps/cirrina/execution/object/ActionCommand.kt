package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.spec.LazyContextVariable
import com.codahale.metrics.MetricRegistry
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
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
    action.expression.evaluate(scope.extent)
    return emptyList()
  }

  private fun executeInvoke(action: InvokeAction, scope: Scope): List<Action> {
    val service =
      selector.select(action.type, action.mode)
        ?: error("no service implementation found for type '${action.type}'")

    val input =
      action.input.map { if (it is LazyContextVariable) it.evaluate(scope.extent) else it }

    coroutineScope.launch {
      val delta = measureTime {
        runCatching { service.invoke(input) }
          .onSuccess { output ->
            action.output.forEach { reference ->
              output
                .firstOrNull { it.name == reference }
                ?.let {
                  runCatching { scope.extent.set(reference, it.value) }
                    .onFailure { e ->
                      logger.warn(e) {
                        "failed to assign service output to variable '${reference}'"
                      }
                    }
                }
                ?: logger.warn {
                  "service output does not contain expected variable '${reference}'"
                }
            }

            action.emits.forEach { conditionalEvent ->
              val shouldEmit = conditionalEvent.provided?.evaluate(scope.extent) ?: true

              if (shouldEmit != false) {
                val emittedEvent = conditionalEvent.event.copy(data = output)
                if (emittedEvent.channel == EventChannel.INTERNAL) {
                  eventHandler.propagateToParent(emittedEvent)
                } else {
                  eventHandler.emit(emittedEvent)
                }
              }
            }
          }
          .onFailure { logger.error(it) { "service invocation failed" } }
      }
      metricRegistry.timer("invoke.time").update(delta.toJavaDuration())
    }

    return emptyList()
  }

  private fun executeMatch(action: MatchAction, scope: Scope): List<Action> =
    action.cases.entries
      .filter { (expression, _) -> expression.evaluate(scope.extent) == true }
      .flatMap { it.value }
      .ifEmpty { listOfNotNull(action.default) }

  private fun executeEmit(action: EmitAction, scope: Scope): List<Action> {
    val emittedEvent =
      action.event.run {
        val evaluatedData =
          data.map { item ->
            if (item is LazyContextVariable) item.evaluate(scope.extent) else item
          }

        copy(data = evaluatedData).let { eventWithData ->
          val target = action.target?.let { source -> source.evaluate(scope.extent) as? String }

          if (target != null) eventWithData.copy(target = target) else eventWithData
        }
      }

    with(eventHandler) {
      if (emittedEvent.channel == EventChannel.INTERNAL) propagateToParent(emittedEvent)
      else emit(emittedEvent)
    }

    return emptyList()
  }

  private fun executeLog(action: LogAction, scope: Scope): List<Action> {
    action.message.evaluate(scope.extent).toString().also { logger.info(it) }
    return emptyList()
  }

  private fun executeCtr(action: CtrAction, scope: Scope): List<Action> {
    val tags =
      action.tags?.entries?.joinToString(".") { (key, value) ->
        "$key=${value.evaluate(scope.extent)}"
      } ?: ""

    val name =
      if (tags.isEmpty()) {
        action.counter
      } else {
        "${action.counter}.$tags"
      }

    metricRegistry.counter(name).inc(action.by)

    return emptyList()
  }
}
