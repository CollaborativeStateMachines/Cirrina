package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.cirrina.di.Main
import at.ac.uibk.dps.cirrina.cirrina.di.Run
import at.ac.uibk.dps.cirrina.execution.`object`.*
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.spec.ContextVariable
import at.ac.uibk.dps.cirrina.spec.Csml as CsmlSpec
import at.ac.uibk.dps.cirrina.spec.Event
import at.ac.uibk.dps.cirrina.spec.Instance
import at.ac.uibk.dps.cirrina.spec.graph.EventGraph
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import jakarta.inject.Inject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class Runtime
@Inject
constructor(
  private val stateMachineFactory: StateMachine.Factory,
  @Run run: List<String>,
  @Main main: URI,
  val metricRegistry: MetricRegistry,
  persistentContext: Context? = null,
) : PropagationHandler {
  val extent = persistentContext?.let { Extent.of(it) } ?: Extent.of()

  val eventHandler = EventHandler(this)

  private val csml =
    CsmlSpec.create(CsmParser.parseCsml(main))
      .onFailure { logger.error(it) { "failed to initialize collaborative state machine class" } }
      .getOrThrow()

  val selector =
    RandomServiceImplementationSelector(ServiceImplementation.from(csml.bindings ?: emptyList()))

  private val graph = EventGraph()

  private val instances = ConcurrentHashMap<String, StateMachine>()

  private val runtimeJob = SupervisorJob()

  private var completion: Timer = metricRegistry.timer("runtime.completionTime")

  init {
    persistentContext?.let { context ->
      csml.collaborativeStateMachine.persistentContext.forEach { (k, v) ->
        runCatching { context.create(k, v.evaluate()) }
          .onFailure { logger.warn { "variable '${k}' already exists or failed to create" } }
      }
    }
    csml.instances.filter { it.name in run }.forEach { instantiate(it) }
  }

  fun instantiate(instance: Instance) {
    val hierarchy =
      stateMachineFactory.createHierarchy(
        name = instance.name,
        spec = instance.stateMachine,
        data = instance.data.map { (k, v) -> ContextVariable(k, v.evaluate()) },
        subscriptions =
          csml.instances.filter { instance.subscription.matches(it.name) }.map { it.name },
        runtime = this,
        parent = null,
      )

    hierarchy.forEach { machine ->
      instances[machine.name] = machine
      graph.addInstance(machine)
    }

    eventHandler.bind(
      graph = graph,
      instanceNames = instances.keys.toList(),
      subscribedTo = instances.values.flatMap { it.subscriptions },
    )

    hierarchy.forEach { machine -> machine.start(parentContext = runtimeJob) }
  }

  fun run() = runBlocking {
    measureTime {
        runtimeJob.children.forEach { it.join() }

        while (runtimeJob.children.any()) {
          runtimeJob.children.toList().joinAll()
        }
      }
      .also {
        completion.update(it.toJavaDuration())
        logger.info { "runtime terminated in $it" }
      }
  }

  fun findStateMachineInstance(stateMachineObjectName: String): StateMachine? =
    instances[stateMachineObjectName]

  override fun invoke(event: Event) {
    instances.values.forEach { it.pushEvent(event) }
  }
}
