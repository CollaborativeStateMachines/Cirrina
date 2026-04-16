package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.Runtime
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.spec.Action
import at.ac.uibk.dps.cirrina.spec.ContextVariable
import at.ac.uibk.dps.cirrina.spec.Ctr
import at.ac.uibk.dps.cirrina.spec.Emit
import at.ac.uibk.dps.cirrina.spec.Eval
import at.ac.uibk.dps.cirrina.spec.Instantiate
import at.ac.uibk.dps.cirrina.spec.Invoke
import at.ac.uibk.dps.cirrina.spec.LazyContextVariable
import at.ac.uibk.dps.cirrina.spec.Log
import at.ac.uibk.dps.cirrina.spec.Match
import at.ac.uibk.dps.cirrina.spec.Reset
import at.ac.uibk.dps.cirrina.spec.Timeout
import com.codahale.metrics.MetricRegistry
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

interface Scope {
  val runtime: Runtime

  val extent: Extent
}

class ActionExecutor(
  private val selector: ServiceImplementationSelector,
  private val metricRegistry: MetricRegistry,
  private val eventHandler: StateMachine.StateMachineEventHandler,
  private val coroutineScope: CoroutineScope,
) {
  fun execute(spec: Action, scope: Scope): List<Action> =
    when (spec) {
      is Invoke -> executeInvoke(spec, scope)
      is Eval -> executeEval(spec, scope)
      is Emit -> executeEmit(spec, scope)
      is Timeout -> listOf(spec.triggers)
      is Reset -> emptyList()
      is Match -> executeMatch(spec, scope)
      is Log -> executeLog(spec, scope)
      is Instantiate -> executeInstantiate(spec, scope)
      is Ctr -> executeCtr(spec, scope)
      else -> error("unknown action type '${spec::class.simpleName}'")
    }

  private fun executeInvoke(spec: Invoke, scope: Scope): List<Action> {
    val service =
      selector.select(spec.type, spec.mode)
        ?: error("no service implementation found for type '${spec.type}'")

    val input = spec.input.map { it.evaluate(scope.extent) }

    coroutineScope.launch {
      val delta = measureTime {
        runCatching { service.invoke(input) }
          .onSuccess { output ->
            spec.output.forEach { reference ->
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

            spec.emits.forEach { conditionalEvent ->
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

  private fun executeEval(spec: Eval, scope: Scope): List<Action> {
    spec.expression.evaluate(scope.extent)
    return emptyList()
  }

  private fun executeEmit(spec: Emit, scope: Scope): List<Action> {
    val emittedEvent =
      spec.event.run {
        val evaluatedData =
          data.map { item ->
            if (item is LazyContextVariable) item.evaluate(scope.extent) else item
          }

        copy(data = evaluatedData).let { eventWithData ->
          val target = spec.target?.let { source -> source.evaluate(scope.extent) as? String }

          if (target != null) eventWithData.copy(target = target) else eventWithData
        }
      }

    with(eventHandler) {
      if (emittedEvent.channel == EventChannel.INTERNAL) propagateToParent(emittedEvent)
      else emit(emittedEvent)
    }

    return emptyList()
  }

  private fun executeMatch(spec: Match, scope: Scope): List<Action> =
    spec.cases.entries
      .filter { (expression, _) -> expression.evaluate(scope.extent) == true }
      .flatMap { it.value }
      .ifEmpty { listOfNotNull(spec.default) }

  private fun executeLog(spec: Log, scope: Scope): List<Action> {
    spec.message.evaluate(scope.extent).toString().also { logger.info(it) }
    return emptyList()
  }

  private fun executeInstantiate(spec: Instantiate, scope: Scope): List<Action> {
    spec.instances.forEach {
      val data = it.data.map { (k, v) -> ContextVariable(k, v.evaluate(scope.extent)) }
      println(data)
    }

    return emptyList()
  }

  private fun executeCtr(spec: Ctr, scope: Scope): List<Action> {
    val tags =
      spec.tags?.entries?.joinToString(".") { (key, value) ->
        "$key=${value.evaluate(scope.extent)}"
      } ?: ""

    val name =
      if (tags.isEmpty()) {
        spec.counter
      } else {
        "${spec.counter}.$tags"
      }

    metricRegistry.counter(name).inc(spec.by)

    return emptyList()
  }
}
