package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.cirrina.di.CsmMain
import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.StateMachine
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.spec.Csml as CsmlSpec
import at.ac.uibk.dps.cirrina.spec.StateMachine as StateMachineSpec
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

class Runtime
@Inject
constructor(
  private val eventHandler: EventHandler,
  private val stateMachineFactory: StateMachine.Factory,
  persistentContext: Context?,
  meterRegistry: MeterRegistry,
  @CsmMain csmMainUri: URI,
) {
  val stateMachineInstances: Map<String, StateMachine>
  val serviceImplementationSelector: ServiceImplementationSelector

  val extent = persistentContext?.let { Extent.of(it) } ?: Extent.of()

  val phaser: Phaser = Phaser(1)

  var completionTimer: Timer = meterRegistry.timer("runtime.completionTime")

  init {
    val csmlSpec =
      CsmlSpec.create(CsmParser.parseCsml(csmMainUri))
        .onFailure { logger.error(it) { "failed to initialize collaborative state machine class" } }
        .getOrThrow()

    persistentContext?.let { context ->
      csmlSpec.collaborativeStateMachineSpec.persistentContextVariables.forEach { variable ->
        runCatching { context.create(variable.name, variable.value) }
          .onFailure {
            logger.warn { "variable '${variable.name}' already exists or failed to create" }
          }
      }
    }

    serviceImplementationSelector =
      RandomServiceImplementationSelector(ServiceImplementation.from(csmlSpec.bindings))

    stateMachineInstances =
      csmlSpec.instances
        .flatMap { (instanceName, stateMachineClass) ->
          buildInstances(
            csmlSpec.collaborativeStateMachineSpec.stateMachineClasses[stateMachineClass]
              ?: error("state machine class '$stateMachineClass' not found"),
            instanceName,
            null,
            csmlSpec.instanceSubscriptions[instanceName],
            csmlSpec.instanceData[instanceName],
          )
        }
        .associateBy { it.instanceName }

    csmlSpec.instanceSubscriptions.values.flatten().forEach { eventHandler.subscribe(it) }

    stateMachineInstances.values.forEach { instance ->
      eventHandler.registerHandler(instance::pushEvent)
    }
  }

  fun findStateMachineInstance(stateMachineObjectName: String): StateMachine? =
    stateMachineInstances[stateMachineObjectName]

  fun run() = runBlocking {
    measureTime {
        stateMachineInstances.values.forEach { it.start() }

        phaser.arriveAndDeregister()

        while (phaser.registeredParties > 0) {
          phaser.awaitAdvance(phaser.phase)
        }
      }
      .also { duration ->
        completionTimer.record(duration.toJavaDuration())
        logger.info { "runtime terminated in $duration" }
      }
  }

  private fun buildInstances(
    stateMachineSpec: StateMachineSpec,
    instanceName: String,
    parentInstance: StateMachine?,
    eventSubscriptions: List<String>?,
    data: List<ContextVariable>?,
  ): List<StateMachine> =
    stateMachineFactory
      .create(
        instanceName,
        this,
        stateMachineSpec,
        serviceImplementationSelector,
        parentInstance,
        eventSubscriptions,
        data,
      )
      .let { currentInstance ->
        stateMachineSpec.nestedStateMachinesSpecs
          .flatMapIndexed { index, nestedStateMachineClass ->
            buildInstances(
              nestedStateMachineClass,
              "${currentInstance.instanceName}.$index@${nestedStateMachineClass.name}",
              currentInstance,
              null,
              null,
            )
          }
          .let { nestedInstances ->
            currentInstance.apply {
              nestedStateMachineInstanceNames = nestedInstances.map { it.instanceName }
            }
            listOf(currentInstance) + nestedInstances
          }
      }
}
