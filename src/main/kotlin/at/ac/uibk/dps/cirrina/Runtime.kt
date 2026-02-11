package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.cirrina.di.CsmMain
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
  @CsmMain csmMain: URI,
) {
  val instances: Map<String, StateMachine>
  val selector: ServiceImplementationSelector

  val extent = persistentContext?.let { Extent.of(it) } ?: Extent.of()

  val phaser: Phaser = Phaser(1)

  var completion: Timer = meterRegistry.timer("runtime.completionTime")

  init {
    val spec =
      CsmlSpec.create(CsmParser.parseCsml(csmMain))
        .onFailure { logger.error(it) { "failed to initialize collaborative state machine class" } }
        .getOrThrow()

    persistentContext?.let { context ->
      spec.collaborativeStateMachine.persistentContext.forEach { variable ->
        runCatching { context.create(variable.name, variable.value) }
          .onFailure {
            logger.warn { "variable '${variable.name}' already exists or failed to create" }
          }
      }
    }

    selector = RandomServiceImplementationSelector(ServiceImplementation.from(spec.bindings))

    instances =
      spec.instances
        .flatMap { (name, `class`) ->
          stateMachineFactory.createHierarchy(
            name,
            spec.collaborativeStateMachine.stateMachines[`class`]
              ?: error("state machine class '${`class`}' not found"),
            this,
            selector,
            null,
            spec.subscriptions[name],
            spec.datas[name],
          )
        }
        .associateBy { it.name }

    spec.subscriptions.values.flatten().forEach { eventHandler.subscribe(it) }

    instances.values.forEach { instance -> eventHandler.registerHandler(instance::pushEvent) }
  }

  fun findStateMachineInstance(stateMachineObjectName: String): StateMachine? =
    instances[stateMachineObjectName]

  fun run() = runBlocking {
    measureTime {
        instances.values.forEach { it.start() }

        phaser.arriveAndDeregister()

        while (phaser.registeredParties > 0) {
          phaser.awaitAdvance(phaser.phase)
        }
      }
      .also { duration ->
        completion.record(duration.toJavaDuration())
        logger.info { "runtime terminated in $duration" }
      }
  }
}
