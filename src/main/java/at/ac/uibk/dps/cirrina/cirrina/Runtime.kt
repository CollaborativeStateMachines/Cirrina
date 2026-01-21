package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventListener
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.CsmParser
import com.google.common.flogger.FluentLogger
import com.lmax.disruptor.BusySpinWaitStrategy
import com.lmax.disruptor.EventHandler as LmaxEventHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import java.net.URI
import java.util.concurrent.Phaser
import kotlinx.coroutines.runBlocking

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

/**
 * The execution engine responsible for managing the lifecycle of state machine instances.
 *
 * @property eventHandler the communication layer for external event ingestion.
 * @property persistentContext the shared storage for long-lived state variables.
 * @property serviceImplementationSelector logic for choosing between multiple service providers.
 */
class Runtime(
  main: URI,
  stateMachineNames: List<String>,
  val eventHandler: EventHandler,
  private val persistentContext: Context,
  private val serviceImplementationSelector: ServiceImplementationSelector,
) : EventListener {

  companion object {
    const val RING_BUFFER_SIZE = 1024
  }

  /** The flat list of all active state machine instances (including nested ones). */
  val stateMachines: List<StateMachine>

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

  init {
    // Resolve the collaborative state machine class
    val collaborativeStateMachineClass =
      CollaborativeStateMachineClassBuilder.from(CsmParser.parseCsml(main))
        .build()
        .onFailure {
          logger.atSevere().withCause(it).log("Failed to initialize collaborative blueprint")
        }
        .getOrThrow()

    // Create all persistent variables
    collaborativeStateMachineClass.persistentContextVariables.forEach { variable ->
      runCatching { persistentContext.create(variable.name, variable.value) }
        .onFailure {
          logger.atWarning().log("Variable '${variable.name}' already exists or failed to create")
        }
    }

    // Build the state machine instances
    stateMachines =
      stateMachineNames
        .mapNotNull { name ->
          collaborativeStateMachineClass.findStateMachineClassByName(name).also {
            if (it == null) logger.atWarning().log("State machine '$name' not found in blueprint")
          }
        }
        .flatMap { buildInstances(it, null) }

    // Create the event handler
    disruptor.handleEventsWith(
      LmaxEventHandler { envelope, _, _ ->
        stateMachines.forEach { it.onReceiveEvent(envelope.event!!) }
      }
    )
    disruptor.start()

    // Register the event handler
    eventHandler.listener = this
  }

  /** Finds a specific state machine instance by its unique UUID string. */
  fun findInstance(stateMachineId: String): StateMachine? =
    stateMachines.firstOrNull { it.id == stateMachineId }

  /** Blocks the current thread until all registered state machines have terminated. */
  fun run() = runBlocking {
    stateMachines.forEach { it.start() }

    // Release the initial party and wait for all machines to deregister
    phaser.arriveAndDeregister()
    while (phaser.registeredParties > 0) {
      phaser.awaitAdvance(phaser.phase)
    }
  }

  private fun buildInstances(
    stateMachineClass: StateMachineClass,
    parentInstance: StateMachine?,
  ): List<StateMachine> =
    StateMachine(stateMachineClass, this, serviceImplementationSelector, parentInstance).let {
      instance ->
      stateMachineClass.nestedStateMachineClasses
        .flatMap { nestedClass -> buildInstances(nestedClass, instance) }
        .let { nestedInstances ->
          instance
            .apply { setNestedStateMachineIds(nestedInstances.map { it.id }) }
            .let { listOf(it) + nestedInstances }
        }
    }

  /** Routes incoming external events into the ring buffer for asynchronous processing. */
  override fun onReceiveEvent(event: Event) {
    disruptor.publishEvent { envelope, _ -> envelope.event = event }
  }
}
