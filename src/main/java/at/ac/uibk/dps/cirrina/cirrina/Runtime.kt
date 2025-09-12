package at.ac.uibk.dps.cirrina.cirrina

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass
import at.ac.uibk.dps.cirrina.execution.`object`.context.Context
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.statemachine.StateMachine
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementation
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector
import at.ac.uibk.dps.cirrina.io.description.CsmlParser
import at.ac.uibk.dps.cirrina.utils.Id
import com.google.common.collect.ArrayListMultimap
import io.opentelemetry.api.OpenTelemetry
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Runtime for executing state machines defined in a Cirrina CSML project.
 *
 * @property openTelemetry The OpenTelemetry instance for tracing state machine execution.
 * @property eventHandler The event handler that will dispatch events to state machines.
 * @property persistentContext The persistent context where state machine variables are stored.
 */
class Runtime(
  private val openTelemetry: OpenTelemetry,
  val eventHandler: EventHandler,
  val persistentContext: Context,
) {
  companion object {
    // Logger used for runtime logging.
    private val logger: Logger = LogManager.getLogger()
  }

  // List of created state machine instances.
  private val stateMachines = mutableListOf<StateMachine>()

  /** Top-level extent. */
  val extent = Extent(persistentContext)

  /**
   * Find a state machine instance by its ID.
   *
   * @param stateMachineId The ID of the state machine instance.
   * @return The state machine instance, or null if not found.
   */
  fun findInstance(stateMachineId: Id): StateMachine? =
    stateMachines.firstOrNull { it.stateMachineInstanceId == stateMachineId }

  /**
   * Run the specified state machines defined in a CSML project.
   *
   * The CSML project is specified by the path to the folder containing a main.pkl file.
   *
   * @param path Path to the folder containing the main (main.pkl) file.
   * @param stateMachineNames List of state machine names to execute.
   */
  fun run(path: String, stateMachineNames: List<String>) {
    val collaborativeStateMachineClass =
      CollaborativeStateMachineClassBuilder.from(
          // A main.pkl file is required in the CSML project
          CsmlParser.parse(Path(path, "main.pkl").pathString)
        )
        .build()

    // Initialize persistent context variables
    collaborativeStateMachineClass.persistentContextVariables.forEach { variable ->
      try {
        logger.info("Creating persistent context variable '{}'", variable.name())
        persistentContext.create(variable.name(), variable.value())
      } catch (_: IOException) {
        logger.info(
          "Did not create persistent context variable '{}', does it already exist?",
          variable.name(),
        )
      }
    }

    runBlocking {
      newInstances(
          stateMachineNames.map { name ->
            collaborativeStateMachineClass.findStateMachineClassByName(name).orElseThrow {
              IllegalArgumentException("No state machine found with name: $name")
            }
          },
          // TODO: Provide the correct service implementation selector
          RandomServiceImplementationSelector(
            ArrayListMultimap.create<String?, ServiceImplementation?>()
          ),
          null, // Top-level state machine has no parent
        )
        .map { callable -> async(Dispatchers.Default) { callable() } }
        .awaitAll()
    }
  }

  // Creates new state machine instances, including nested state machines.
  private fun newInstances(
    stateMachineClasses: List<StateMachineClass>,
    serviceImplementationSelector: ServiceImplementationSelector,
    parentInstanceId: Id?,
  ): List<() -> Id> =
    stateMachineClasses.map { stateMachineClass ->
      {
        // Create the parent state machine, run it, and acquire the instance ID
        val parentStateMachineInstanceId =
          stateMachine(stateMachineClass, serviceImplementationSelector, parentInstanceId)()

        // Create the nested state machines, run them, and store their instance IDs
        val nestedStateMachineIds =
          newInstances(
              stateMachineClass.nestedStateMachineClasses,
              serviceImplementationSelector,
              parentStateMachineInstanceId,
            )
            .map { it() }

        // Associate the nested state machines with the parent state machine
        findInstance(parentStateMachineInstanceId)?.setNestedStateMachineIds(nestedStateMachineIds)

        parentStateMachineInstanceId
      }
    }

  // Creates and runs a single state machine instance.
  private fun stateMachine(
    stateMachineClass: StateMachineClass,
    serviceImplementationSelector: ServiceImplementationSelector?,
    parentInstanceId: Id?,
  ): () -> Id = {
    // Attempt to find the parent state machine instance
    val parentInstance =
      parentInstanceId?.let { findInstance(it) }
        ?: if (parentInstanceId != null)
          throw UnsupportedOperationException(
            "Parent state machine instance with ID '$parentInstanceId' not found"
          )
        else null

    // Create the state machine instance
    val stateMachineInstance =
      StateMachine(
        this,
        stateMachineClass,
        serviceImplementationSelector,
        openTelemetry, // TODO: Switching to dependency injection would clean this up
        parentInstance,
      )

    // Add the state machine as an event listener
    eventHandler.addListener(stateMachineInstance)

    // Register the state machine instance with the runtime
    stateMachines.add(stateMachineInstance)

    // Run the state machine
    stateMachineInstance.run()

    stateMachineInstance.stateMachineInstanceId
  }
}
