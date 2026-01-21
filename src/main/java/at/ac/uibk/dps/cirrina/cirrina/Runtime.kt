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
import com.lmax.disruptor.BlockingWaitStrategy
import com.lmax.disruptor.EventHandler as LmaxEventHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import java.net.URI
import java.util.concurrent.Phaser
import kotlinx.coroutines.runBlocking

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

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

  val stateMachines: List<StateMachine>

  val extent = Extent.of(persistentContext)

  val phaser = Phaser(1)

  private class EventEnvelope {
    var event: Event? = null
  }

  private val disruptor =
    Disruptor(
      { EventEnvelope() },
      RING_BUFFER_SIZE,
      DaemonThreadFactory.INSTANCE,
      ProducerType.MULTI,
      BlockingWaitStrategy(),
    )

  init {
    val collaborativeStateMachineClass =
      CollaborativeStateMachineClassBuilder.from(CsmParser.parseCsml(main))
        .build()
        .onFailure { error ->
          logger
            .atSevere()
            .withCause(error)
            .log("failed to initialize collaborative state machine class")
        }
        .getOrThrow()

    logger.atFine().log("creating persistent context variables")
    collaborativeStateMachineClass.persistentContextVariables.forEach { variable ->
      runCatching {
          logger.atFiner().log("creating persistent context variable '${variable.name}'")
          persistentContext.create(variable.name, variable.value)
        }
        .onFailure { _ ->
          logger.atWarning().log("did not create persistent context variable '${variable.name}'")
        }
    }

    stateMachines =
      stateMachineNames
        .mapNotNull { name ->
          collaborativeStateMachineClass.findStateMachineClassByName(name)
            ?: run {
              logger.atWarning().log("d state machine with name '$name' could not be instantiated")
              null
            }
        }
        .flatMap { buildInstances(it, null) }

    disruptor.handleEventsWith(
      LmaxEventHandler { envelope, _, _ ->
        stateMachines.forEach { it.onReceiveEvent(envelope.event!!) }
      }
    )
    disruptor.start()

    eventHandler.listener = this
  }

  fun findInstance(stateMachineId: String): StateMachine? =
    stateMachines.firstOrNull { it.id == stateMachineId }

  fun run() = runBlocking {
    stateMachines.forEach { it.start() }

    phaser.arriveAndDeregister()

    while (phaser.registeredParties > 0) {
      println(phaser.registeredParties)
      phaser.awaitAdvance(phaser.phase)
    }
  }

  private fun buildInstances(
    stateMachineClass: StateMachineClass,
    parentInstance: StateMachine?,
  ): List<StateMachine> {
    val instance =
      StateMachine(stateMachineClass, this, serviceImplementationSelector, parentInstance)

    val nestedInstances =
      stateMachineClass.nestedStateMachineClasses.flatMap { buildInstances(it, instance) }

    instance.setNestedStateMachineIds(nestedInstances.map { it.id })
    return listOf(instance) + nestedInstances
  }

  override fun onReceiveEvent(event: Event) {
    disruptor.publishEvent { envelope, _ -> envelope.event = event }
  }
}
