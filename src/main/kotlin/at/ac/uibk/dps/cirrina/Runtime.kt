package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.cirrina.di.Main
import at.ac.uibk.dps.cirrina.cirrina.di.Run
import at.ac.uibk.dps.cirrina.execution.graph.EventGraph
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.StateMachine
import at.ac.uibk.dps.cirrina.execution.`object`.createHierarchy
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.spec.Csml as CsmlSpec
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.inject.Inject
import java.net.URI
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class Runtime
@Inject
constructor(
  @Run private val run: List<String>,
  @Main main: URI,
  persistentContext: Context?,
  stateMachineFactory: StateMachine.Factory,
  val meterRegistry: MeterRegistry,
) {
  val eventHandler = EventHandler()
  val extent = persistentContext?.let { Extent.of(it) } ?: Extent.of()

  val selector: ServiceImplementationSelector

  private val instances: Map<String, StateMachine>

  private val graph: EventGraph

  private var completion: Timer = meterRegistry.timer("runtime.completionTime")

  init {
    val spec =
      CsmlSpec.create(CsmParser.parseCsml(main))
        .onFailure { logger.error(it) { "failed to initialize collaborative state machine class" } }
        .getOrThrow()

    selector =
      RandomServiceImplementationSelector(ServiceImplementation.from(spec.bindings ?: emptyList()))

    persistentContext?.let { context ->
      spec.collaborativeStateMachine.persistentContext.forEach { variable ->
        runCatching { context.create(variable.name, variable.value) }
          .onFailure {
            logger.warn { "variable '${variable.name}' already exists or failed to create" }
          }
      }
    }

    instances =
      spec.instances
        .filter { it.name in run }
        .flatMap { instance ->
          stateMachineFactory.createHierarchy(
            name = instance.name,
            specification = instance.stateMachine,
            instance = instance,
            subscriptions =
              spec.instances.filter { instance.subscription.matches(it.name) }.map { it.name },
            runtime = this,
            parent = null,
          )
        }
        .associateBy { it.name }

    graph = EventGraph.create(spec.instances)

    eventHandler.bind(
      graph = graph,
      instanceNames = run,
      subscribedTo = instances.values.flatMap { it.subscriptions },
      handlers = instances.values.map { it::pushEvent },
    )
  }

  fun run() = runBlocking {
    measureTime { instances.values.map { it.start() }.joinAll() }
      .also {
        completion.record(it.toJavaDuration())
        logger.info { "runtime terminated in $it" }
      }
  }

  fun findStateMachineInstance(stateMachineObjectName: String): StateMachine? =
    instances[stateMachineObjectName]
}
