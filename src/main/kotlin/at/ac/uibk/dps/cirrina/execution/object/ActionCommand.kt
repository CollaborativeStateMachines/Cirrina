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
  val instanceRegistry: Runtime.InstanceRegistry

  val extent: Extent
}

class ActionExecutor(
  private val selector: ServiceImplementationSelector,
  private val metricRegistry: MetricRegistry,
  private val eventHandler: StateMachine.StateMachineEventHandler,
  private val coroutineScope: CoroutineScope,
) {
  fun execute(specification: Action, scope: Scope): List<Action> =
    when (specification) {
      is Invoke -> executeInvoke(specification, scope)
      is Eval -> executeEval(specification, scope)
      is Emit -> executeEmit(specification, scope)
      is Timeout -> listOf(specification.triggers)
      is Reset -> emptyList()
      is Match -> executeMatch(specification, scope)
      is Log -> executeLog(specification, scope)
      is Instantiate -> executeInstantiate(specification, scope)
      is Ctr -> executeCtr(specification, scope)
      else -> error("unknown action type '${specification::class.simpleName}'")
    }

  private fun executeInvoke(specification: Invoke, scope: Scope): List<Action> {
    val service =
      selector.select(specification.type, specification.mode)
        ?: error("no service implementation found for type '${specification.type}'")

    val input = specification.input.map { it.evaluate(scope.extent) }

    coroutineScope.launch {
      val delta = measureTime {
        runCatching { service.invoke(input) }
          .onSuccess { output ->
            specification.output.forEach { reference ->
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

            specification.emits.forEach { conditionalEvent ->
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

  private fun executeEval(specification: Eval, scope: Scope): List<Action> {
    specification.expression.evaluate(scope.extent)
    return emptyList()
  }

  private fun executeEmit(specification: Emit, scope: Scope): List<Action> {
    val emittedEvent =
      specification.event.run {
        val evaluatedData =
          data.map { item ->
            if (item is LazyContextVariable) item.evaluate(scope.extent) else item
          }

        copy(data = evaluatedData).let { eventWithData ->
          val target =
            specification.target?.let { source -> source.evaluate(scope.extent) as? String }

          if (target != null) eventWithData.copy(target = target) else eventWithData
        }
      }

    with(eventHandler) {
      if (emittedEvent.channel == EventChannel.INTERNAL) propagateToParent(emittedEvent)
      else emit(emittedEvent)
    }

    return emptyList()
  }

  private fun executeMatch(specification: Match, scope: Scope): List<Action> =
    specification.cases.entries
      .filter { (expression, _) -> expression.evaluate(scope.extent) == true }
      .flatMap { it.value }
      .ifEmpty { listOfNotNull(specification.default) }

  private fun executeLog(spec: Log, scope: Scope): List<Action> {
    spec.message.evaluate(scope.extent).toString().also { logger.info(it) }
    return emptyList()
  }

  private fun executeInstantiate(specification: Instantiate, scope: Scope): List<Action> {
    val instances = specification.instances.map { it.evaluate(scope.extent).getOrThrow() }

    instances.forEach {
      val instanceData = it.data.map { (k, v) -> ContextVariable(k, v.evaluate(scope.extent)) }

      scope.instanceRegistry.instantiate(it, instanceData)
    }

    return emptyList()
  }

  private fun executeCtr(specification: Ctr, scope: Scope): List<Action> {
    val tags =
      specification.tags?.entries?.joinToString(".") { (key, value) ->
        "$key=${value.evaluate(scope.extent)}"
      } ?: ""

    val name =
      if (tags.isEmpty()) {
        specification.counter
      } else {
        "${specification.counter}.$tags"
      }

    metricRegistry.counter(name).inc(specification.by)

    return emptyList()
  }
}
