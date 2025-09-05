package at.ac.uibk.dps.cirrina.runtime;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass;
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass;
import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.execution.object.statemachine.StateMachine;
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector;
import at.ac.uibk.dps.cirrina.utils.Id;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Abstract runtime, provides the generic runtime behavior of running state machine instances.
 * <p>
 * The runtime is implemented as either an online, or offline runtime.
 * <p>
 * The offline runtime is meant to be disconnected from any online dependency such as a message queue, key-value or coordination system and
 * requires manual instantiation of state machines.
 * <p>
 * Conversely, the online runtime is meant to be connected to online services such as mentioned above and serves as a fully distributed
 * system.
 *
 * @see OfflineRuntime
 * @see OnlineRuntime
 */
public abstract class Runtime implements EventListener {

  /**
   * Runtime logger.
   */
  protected static final Logger logger = LogManager.getLogger();

  /**
   * Runtime name.
   */
  protected final String name;

  /**
   * Event handler.
   */
  protected final EventHandler eventHandler;

  /**
   * Persistent context.
   */
  protected final Context persistentContext;

  /**
   * OpenTelemetry.
   */
  protected final OpenTelemetry openTelemetry;

  /**
   * Runtime OpenTelemetry tracer.
   */
  protected final Tracer tracer;

  /**
   * Runtime OpenTelemetry meter.
   */
  protected final Meter meter;

  /**
   * StateClass machine instance executor service, manages running state machine instances.
   */
  private final ExecutorService stateMachineInstanceExecutorService =
    Executors.newCachedThreadPool();

  /**
   * List of instantiated state machines.
   */
  private final Queue<StateMachine> stateMachines = new ConcurrentLinkedQueue<>();

  /**
   * Initializes this runtime instance.
   *
   * @param name              Name.
   * @param eventHandler      Event handler.
   * @param persistentContext Persistent context.
   * @param openTelemetry     OpenTelemetry.
   */
  public Runtime(
    String name,
    EventHandler eventHandler,
    Context persistentContext,
    OpenTelemetry openTelemetry
  ) {
    this.name = name;

    // Keep dependencies
    this.eventHandler = eventHandler;
    this.persistentContext = persistentContext;
    this.openTelemetry = openTelemetry;

    // Create an OpenTelemetry tracer
    this.tracer = this.openTelemetry.getTracer("runtime");

    // Create an OpenTelemetry meter
    meter = this.openTelemetry.getMeter("runtime");
  }

  /**
   * Find a state machine instance based on its instance id.
   *
   * @param stateMachineId StateClass machine instance id.
   * @return The state machine instance or an empty optional if no state machine instance was found for the given instance id.
   */
  public Optional<StateMachine> findInstance(Id stateMachineId) {
    return stateMachines
      .stream()
      .filter(stateMachineInstance ->
        stateMachineInstance.getStateMachineInstanceId().equals(stateMachineId)
      )
      .findFirst();
  }

  public Optional<StateMachine> findInstance(String stateMachineId) {
    return stateMachines
      .stream()
      .filter(stateMachineInstance -> stateMachineInstance.getId().equals(stateMachineId))
      .findFirst();
  }

  /**
   * Instantiates all state machines of a collaborative state machine.
   * <p>
   * This method exists for convenience, instantiating a full collaborative state machine is not a typical scenario. However, it may be
   * useful for testing.
   *
   * @param collaborativeStateMachineClass Collaborative state machine.
   * @param serviceImplementationSelector  Service implementation selector.
   * @param endTime                        The end time of this instance.
   * @return Instance IDs.
   * @throws UnsupportedOperationException If a state machine could not be instantiated.
   */
  protected List<Id> newInstance(
    CollaborativeStateMachineClass collaborativeStateMachineClass,
    ServiceImplementationSelector serviceImplementationSelector,
    double endTime
  ) throws UnsupportedOperationException {
    return newInstances(
      collaborativeStateMachineClass.getStateMachineClasses(),
      serviceImplementationSelector,
      null,
      endTime,
      null
    );
  }

  /**
   * Instantiate a collection of state machines.
   *
   * @param stateMachineClasses           StateClass machines to instantiate.
   * @param serviceImplementationSelector Service implementation selector.
   * @param parentInstanceId              ID of parent state machine instance.
   * @param endTime                       The end time of this instance.
   * @return Instance IDs.
   * @throws UnsupportedOperationException If a state machine could not be instantiated.
   */
  public List<Id> newInstances(
    List<StateMachineClass> stateMachineClasses,
    ServiceImplementationSelector serviceImplementationSelector,
    @Nullable Id parentInstanceId,
    double endTime,
    @Nullable Extent extent
  ) throws UnsupportedOperationException {
    var instanceIds = new ArrayList<Id>();

    for (var stateMachine : stateMachineClasses) {
      // Instantiate the state machine instance hierarchy
      try {
        // Parent
        var instanceId = newInstance(
          stateMachine,
          serviceImplementationSelector,
          parentInstanceId,
          endTime,
          extent
        );
        instanceIds.add(instanceId);

        final var nestedStateMachineIds = new ArrayList<Id>();

        // Add nested state machines
        if (!stateMachine.getNestedStateMachineClasses().isEmpty()) {
          nestedStateMachineIds.addAll(
            newInstances(
              stateMachine.getNestedStateMachineClasses(),
              serviceImplementationSelector,
              instanceId,
              endTime,
              extent
            )
          );
        }

        // Add to the collection of created state machine instance IDs
        instanceIds.addAll(nestedStateMachineIds);

        // Provide the parent state machine with the IDs of its children
        findInstance(instanceId).get().setNestedStateMachineIds(nestedStateMachineIds);
      } catch (UnsupportedOperationException e) {
        throw new UnsupportedOperationException("Could not instantiate state machine", e);
      }
    }

    return instanceIds;
  }

  /**
   * Instantiate a state machine.
   * <p>
   * The runtime cannot be shut down, otherwise an exception is thrown.
   *
   * @param stateMachineClass             StateClass machine to instantiate.
   * @param serviceImplementationSelector Service implementation selector.
   * @param parentInstanceId              ID of parent state machine instance.
   * @param endTime                       The end time of this instance.
   * @return Instance ID.
   * @throws UnsupportedOperationException If the runtime is shut down.
   * @throws UnsupportedOperationException If the parent state machine could not be found.
   */
  protected Id newInstance(
    StateMachineClass stateMachineClass,
    ServiceImplementationSelector serviceImplementationSelector,
    @Nullable Id parentInstanceId,
    double endTime,
    @Nullable Extent extent
  ) throws UnsupportedOperationException {
    if (stateMachineInstanceExecutorService.isShutdown()) {
      throw new UnsupportedOperationException("Runtime is shut down");
    }

    final var stateMachineName = stateMachineClass.getName();
    final var parentIdAsString = parentInstanceId == null ? "" : parentInstanceId.toString();

    logger.info(
      "Creating new instance of '{}' - parent is '{}'...",
      stateMachineName,
      parentIdAsString
    );

    // Find the parent state machine instance
    final var parentInstance = parentInstanceId == null
      ? null
      : findInstance(parentInstanceId).orElse(null);

    if (parentInstanceId != null && parentInstance == null) {
      throw new UnsupportedOperationException(
        "The parent state machine instance with ID '%s' could not be found".formatted(
          parentInstanceId.toString()
        )
      );
    }

    // Create the state machine instance
    final var stateMachineInstance = new StateMachine(
      this,
      stateMachineClass,
      serviceImplementationSelector,
      openTelemetry,
      parentInstance,
      endTime,
      extent
    );

    // Add event listener to the event handler
    eventHandler.addListener(stateMachineInstance);

    // Add to the collection of state machine instances
    stateMachines.add(stateMachineInstance);

    // Execute
    stateMachineInstanceExecutorService.submit(stateMachineInstance);

    final var stateMachineInstanceId = stateMachineInstance.getStateMachineInstanceId();

    logger.info(
      "Created an instance of '{}' with ID '{}'",
      stateMachineName,
      stateMachineInstanceId.toString()
    );

    return stateMachineInstanceId;
  }

  public void remove(StateMachine stateMachine) {
    stateMachines.remove(stateMachine);
  }

  /**
   * Run to completion given the currently instantiated state machines.
   * <p>
   * This method will block until all state machines are terminated or the timeout has been reached.
   * <p>
   * No new state machines can be instantiated for the duration of this method's execution.
   * <p>
   * No state machines can be instantiated after this method has been invoked.
   *
   * @param timeoutInMs Timeout in milliseconds.
   * @return True if the waiting has reached completion, otherwise false.
   */
  public boolean waitForCompletion(int timeoutInMs) {
    try {
      shutdown();

      // Wait for completion
      return stateMachineInstanceExecutorService.awaitTermination(
        timeoutInMs,
        TimeUnit.MILLISECONDS
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return false;
  }

  /**
   * Shutdown, will trigger all currently executing state machine instances to be completed and no new instances to be accepted.
   *
   * @throws UnsupportedOperationException If the runtime is already shut down.
   */
  public void shutdown() throws UnsupportedOperationException {
    if (stateMachineInstanceExecutorService.isShutdown()) {
      throw new UnsupportedOperationException("Runtime is already shut down");
    }

    stateMachineInstanceExecutorService.shutdown();
  }

  /**
   * Returns a flag that indicates if this runtime is shut down.
   *
   * @return True if shut down, otherwise false.
   */
  public boolean isShutdown() {
    return stateMachineInstanceExecutorService.isShutdown();
  }

  /**
   * Returns this runtime's extent.
   *
   * @return Extent.
   */
  public Extent getExtent() {
    return new Extent(persistentContext);
  }

  /**
   * Returns this event handler.
   *
   * @return Event handler.
   */
  public EventHandler getEventHandler() {
    return eventHandler;
  }

  /**
   * Returns the number of state machines.
   *
   * @return Number of state machines.
   */
  public int getNumStateMachines() {
    return stateMachines.size();
  }
}
