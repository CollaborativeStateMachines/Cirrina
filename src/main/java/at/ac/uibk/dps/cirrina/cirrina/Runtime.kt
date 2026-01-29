package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.cirrina.di.CsmMain
import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CsmlClassBuilder
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.CsmParser
import com.lmax.disruptor.BusySpinWaitStrategy
import com.lmax.disruptor.EventHandler as LmaxEventHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.inject.Inject
import java.net.URI
import java.util.concurrent.Phaser
import kotlin.collections.component1
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The execution engine responsible for managing the lifecycle of state machine instances.
 *
 * @property eventHandler the communication layer for external event ingestion.
 * @property persistentContext the shared storage for long-lived state variables.
 * @property serviceImplementationSelector logic for choosing between multiple service providers.
 * @property stateMachineFactory factory for creating state machine instances.
 * @property meterRegistry the registry used for collecting metrics.
 * @property csmMainUri the URI of the main collaborative state machine definition.
 */
class Runtime
@Inject
constructor(
  private val eventHandler: EventHandler,
  private val persistentContext: Context,
  private val serviceImplementationSelector: ServiceImplementationSelector,
  private val stateMachineFactory: StateMachine.Factory,
  meterRegistry: MeterRegistry,
  @CsmMain csmMainUri: URI,
) : EventListener {

  companion object {
    const val RING_BUFFER_SIZE = 1024
  }

  /** The flat list of all active state machine instances (including nested ones). */
  val stateMachineInstances: Map<String, StateMachine>

  /** The shared extent used by all managed state machines. */
  val extent: Extent = Extent.of(persistentContext)

  /** Synchronization barrier used to track the completion of all state machine lifecycles. */
  val phaser: Phaser = Phaser(1)

  private class EventEnvelope {
    var event: Event? = null
  }

  private val disruptor =
    Disruptor(
      { EventEnvelope() },
      RING_BUFFER_SIZE,
      DaemonThreadFactory.INSTANCE,
      ProducerType.MULTI,
      BusySpinWaitStrategy(),
    )

  var completionTimer: Timer = meterRegistry.timer("runtime.completionTime")

  init {
    // Resolve the collaborative state machine class
    val csmlClass =
      CsmlClassBuilder.from(CsmParser.parseCsml(csmMainUri))
        .build()
        .onFailure { logger.error(it) { "failed to initialize collaborative state machine class" } }
        .getOrThrow()

    // Create all persistent variables
    csmlClass.collaborativeStateMachineClass.persistentContextVariables.forEach { variable ->
      runCatching { persistentContext.create(variable.name, variable.value) }
        .onFailure {
          logger.warn { "variable '${variable.name}' already exists or failed to create" }
        }
    }

    // Build the state machine instances
    stateMachineInstances =
      csmlClass.instantiate
        .flatMap { (instanceName, stateMachineClass) ->
          buildInstances(
            csmlClass.collaborativeStateMachineClass.findStateMachineClassByName(stateMachineClass)
              ?: error("state machine class '$stateMachineClass' not found"),
            instanceName,
            null,
            csmlClass.subscriptions[instanceName],
          )
        }
        .associateBy { it.instanceName }

    // Subscribe to all external events according to the subscriptions
    csmlClass.subscriptions.values.flatten().forEach { eventHandler.subscribe(it) }

    // Create the event handler
    disruptor.handleEventsWith(
      LmaxEventHandler { envelope, _, _ ->
        stateMachineInstances.values.forEach { it.onReceiveEvent(envelope.event!!) }
      }
    )
    disruptor.start()

    // Register the event handler
    eventHandler.listener = this
  }

  /** Finds a specific state machine instance by its object name. */
  fun findStateMachineInstance(stateMachineObjectName: String): StateMachine? =
    stateMachineInstances[stateMachineObjectName]

  /** Blocks the current thread until all registered state machines have terminated. */
  fun run() = runBlocking {
    measureTime {
        stateMachineInstances.values.forEach { it.start() }

        // Release the initial party...
        phaser.arriveAndDeregister()

        // and wait for all machines to deregister
        while (phaser.registeredParties > 0) {
          phaser.awaitAdvance(phaser.phase)
        }
      }
      .also { duration ->
        completionTimer.record(duration.toJavaDuration())

        logger.info { "all state machines terminated in $duration" }
      }
  }

  private fun buildInstances(
    stateMachineClass: StateMachineClass,
    instanceName: String,
    parentInstance: StateMachine?,
    eventSubscriptions: List<String>?,
  ): List<StateMachine> =
    stateMachineFactory
      // Create a state machine instance
      .create(instanceName, this, stateMachineClass, parentInstance, eventSubscriptions)
      // With the parent instance...
      .let { currentInstance ->
        stateMachineClass.nestedStateMachineClasses
          .flatMapIndexed { index, nestedStateMachineClass ->
            // build the nested state machine instances...
            buildInstances(
              nestedStateMachineClass,
              "${currentInstance.instanceName}.$index@${nestedStateMachineClass.name}",
              currentInstance,
              null,
            )
          }
          .let { nestedInstances ->
            // add the nested instance names to the parent instance...
            currentInstance.apply {
              nestedStateMachineInstanceNames = nestedInstances.map { it.instanceName }
            }
            // and return the parent instance and the nested instances
            listOf(currentInstance) + nestedInstances
          }
      }

  /** Routes incoming external events into the ring buffer for asynchronous processing. */
  override fun onReceiveEvent(event: Event) {
    disruptor.publishEvent { envelope, _ -> envelope.event = event }
  }
}
