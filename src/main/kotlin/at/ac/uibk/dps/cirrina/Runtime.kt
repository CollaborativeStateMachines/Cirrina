package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.cirrina.di.Main
import at.ac.uibk.dps.cirrina.cirrina.di.Run
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
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class Runtime
@Inject
constructor(
  @Run private val run: List<String>,
  private val eventHandler: EventHandler,
  private val stateMachineFactory: StateMachine.Factory,
  persistentContext: Context?,
  meterRegistry: MeterRegistry,
  @Main main: URI,
) {
  val instances: Map<String, StateMachine>
  val selector: ServiceImplementationSelector

  val extent = persistentContext?.let { Extent.of(it) } ?: Extent.of()

  val phaser: Phaser = Phaser(1)

  var completion: Timer = meterRegistry.timer("runtime.completionTime")

  init {
    // Parse the CSML specification
    val spec =
      CsmlSpec.create(CsmParser.parseCsml(main))
        .onFailure { logger.error(it) { "failed to initialize collaborative state machine class" } }
        .getOrThrow()

    // Populate the persistent context
    persistentContext?.let { context ->
      spec.collaborativeStateMachine.persistentContext.forEach { variable ->
        runCatching { context.create(variable.name, variable.value) }
          .onFailure {
            logger.warn { "variable '${variable.name}' already exists or failed to create" }
          }
      }
    }

    // Instantiate a service implementation selector, this can be extended to more selector
    // implementations in the future
    selector =
      RandomServiceImplementationSelector(ServiceImplementation.from(spec.bindings ?: emptyList()))

    // Create the state machines specified
    instances =
      spec.instances
        .filter { it.name in run }
        .flatMap {
          stateMachineFactory.createHierarchy(
            name = it.name,
            specification = it.stateMachine,
            instance = it,
            runtime = this,
            selector = selector,
            parent = null,
          )
        }
        .associateBy { it.name }

    instances.values.forEach { instance -> eventHandler.registerHandler(instance::pushEvent) }
  }

  fun findStateMachineInstance(stateMachineObjectName: String): StateMachine? =
    instances[stateMachineObjectName]

  fun run() = runBlocking {
    measureTime {
        // Start all state machines
        instances.values.forEach { it.start() }

        // Wait for all state machines to finish
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
