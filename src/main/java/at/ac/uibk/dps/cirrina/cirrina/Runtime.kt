package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.utils.Id
import com.google.common.flogger.FluentLogger
import io.opentelemetry.api.OpenTelemetry
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

/**
 * Runtime for executing state machines defined in a Cirrina CSML project.
 *
 * @param main main (main.pkl) URI.
 * @param stateMachineNames list of state machine names to execute.
 * @property openTelemetry the OpenTelemetry instance for tracing state machine execution.
 * @property serviceImplementationSelector the service implementation selector.
 * @property eventHandler the event handler that will dispatch events to state machines.
 * @property persistentContext the persistent context where state machine variables are stored.
 */
class Runtime(
  main: URI,
  stateMachineNames: List<String>,
  private val openTelemetry: OpenTelemetry,
  private var serviceImplementationSelector: ServiceImplementationSelector,
  val eventHandler: EventHandler,
  val persistentContext: Context,
) {
  /** Instantiated state machines. */
  val stateMachines: List<StateMachine>

  /** Top-level extent. */
  val extent = Extent.of(persistentContext)

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
  }

  /**
   * Find a state machine instance by its ID.
   *
   * @param stateMachineId the ID of the state machine instance.
   * @return the state machine instance, or null if not found.
   */
  fun findInstance(stateMachineId: Id): StateMachine? =
    stateMachines.firstOrNull { it.getStateMachineInstanceId() == stateMachineId }

  /** Run all state machines (blocking). */
  fun run() = runBlocking {
    stateMachines.map { instance -> async(Dispatchers.Default) { instance.run() } }.awaitAll()
  }

  // Recursively builds all state machine instances and returns them in a flat list.
  private fun buildInstances(
    stateMachineClass: StateMachineClass,
    parentInstance: StateMachine?,
  ): List<StateMachine> {
    val instance =
      StateMachine(
          this,
          stateMachineClass,
          serviceImplementationSelector,
          openTelemetry,
          parentInstance,
        )
        .also { eventHandler.addListener(it) }

    val nestedInstances =
      stateMachineClass.nestedStateMachineClasses.flatMap { buildInstances(it, instance) }

    instance.setNestedStateMachineIds(nestedInstances.map { it.getStateMachineInstanceId() })
    return listOf(instance) + nestedInstances
  }
}
