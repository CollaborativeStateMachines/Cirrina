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
import kotlin.collections.component1
import kotlin.collections.component2
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
  @Run run: List<String>,
  @Main main: URI,
  metricRegistry: MetricRegistry,
  persistentContext: Context?,
  stateMachineFactory: StateMachine.Factory,
) : PropagationHandler {
  val extent = persistentContext?.let { Extent.of(it) } ?: Extent.of()

  val eventHandler = EventHandler(this)

  private val csml =
    CsmlSpec.create(CsmParser.parseCsml(main))
      .onFailure { logger.error(it) { "failed to initialize collaborative state machine class" } }
      .getOrThrow()

  val serviceImplementationSelector =
    RandomServiceImplementationSelector(ServiceImplementation.from(csml.bindings ?: emptyList()))

  private val runtimeJob = SupervisorJob()

  private var completion: Timer = metricRegistry.timer("runtime.completionTime")

  inner class InstanceRegistry(private val stateMachineFactory: StateMachine.Factory) {
    private val graph = EventGraph()

    val instances: List<StateMachine>
      get() = graph.instances

    fun instantiate(
      instance: Instance,
      instanceData: List<ContextVariable> =
        instance.data.map { (k, v) -> ContextVariable(k, v.evaluate()) },
    ) {
      val hierarchy =
        stateMachineFactory.createHierarchy(
          name = instance.name,
          specification = instance.stateMachine,
          instanceData = instanceData,
          instanceSubscription = instance.subscription,
          instanceRegistry = this,
          parent = null,
          runtimeExtent = extent,
          eventHandler = eventHandler,
          serviceImplementationSelector = serviceImplementationSelector,
        )

      hierarchy.forEach { machine -> graph.addInstance(machine) }

      val instances = graph.instances

      eventHandler.addPublishers(graph.getOutgoing(instances).map { it.event })
      eventHandler.addSubscribers(instances.flatMap { it.subscriptions })

      hierarchy.forEach { machine -> machine.start(parentContext = runtimeJob) }
    }

    fun findStateMachineInstance(name: String): StateMachine? =
      graph.instances.filter { it.name == name }.firstOrNull()
  }

  private val instanceRegistry = InstanceRegistry(stateMachineFactory)

  init {
    persistentContext?.let { context ->
      csml.collaborativeStateMachine.persistentContext.forEach { (k, v) ->
        runCatching { context.create(k, v.evaluate()) }
          .onFailure { logger.warn { "variable '${k}' already exists or failed to create" } }
      }
    }
    csml.instances.filter { it.name in run }.forEach { instanceRegistry.instantiate(it) }
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

  override fun invoke(event: Event) {
    instanceRegistry.instances.forEach { it.pushEvent(event) }
  }
}
