package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.parsing.CsmParser
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
 * @param main Main (main.pkl) URI.
 * @param stateMachineNames List of state machine names to execute.
 * @property openTelemetry The OpenTelemetry instance for tracing state machine execution.
 * @property serviceImplementationSelector The service implementation selector.
 * @property eventHandler The event handler that will dispatch events to state machines.
 * @property persistentContext The persistent context where state machine variables are stored.
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
  val extent = Extent(persistentContext)

  init {
    val collaborativeStateMachineClass =
      CollaborativeStateMachineClassBuilder.from(CsmParser.parseCsml(main)).build()

    logger.atFine().log("Creating persistent context variables")
    collaborativeStateMachineClass.persistentContextVariables.forEach { variable ->
      runCatching {
          logger.atFiner().log("Creating persistent context variable '${variable.name()}'")
          persistentContext.create(variable.name(), variable.value())
        }
        .onFailure { _ ->
          logger
            .atWarning()
            .log(
              "Did not create persistent context variable '${variable.name()}', does it already exist?"
            )
        }
    }

    stateMachines =
      stateMachineNames
        .mapNotNull { name ->
          collaborativeStateMachineClass.findStateMachineClassByName(name)
            ?: run {
              logger
                .atWarning()
                .log(
                  "A state machine with name '$name' could not be instantiated, because it does not exist"
                )
              null
            }
        }
        .flatMap { buildInstances(it, null) }
  }

  /**
   * Find a state machine instance by its ID.
   *
   * @param stateMachineId The ID of the state machine instance.
   * @return The state machine instance, or null if not found.
   */
  fun findInstance(stateMachineId: Id): StateMachine? =
    stateMachines.firstOrNull { it.stateMachineInstanceId == stateMachineId }

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

    instance.setNestedStateMachineIds(nestedInstances.map { it.stateMachineInstanceId })
    return listOf(instance) + nestedInstances
  }
}
