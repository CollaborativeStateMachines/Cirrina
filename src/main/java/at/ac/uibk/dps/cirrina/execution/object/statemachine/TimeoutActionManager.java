package at.ac.uibk.dps.cirrina.execution.object.statemachine;

import at.ac.uibk.dps.cirrina.observability.MethodName;
import at.ac.uibk.dps.cirrina.observability.logging.Log;
import at.ac.uibk.dps.cirrina.observability.tracing.Trace;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Timeout action manager, a manager for timeout tasks that encapsulate the execution of timeout actions.
 * <p>
 * This class is thread-safe.
 */
public final class TimeoutActionManager {

  /**
   * The number of threads in the timeout scheduled thread pool.
   */
  private final int NUM_THREADS = 1;

  /**
   * Timeout task scheduler, configured with a pre-defined number of threads.
   */
  private final ScheduledExecutorService timeoutTaskScheduler = Executors.newScheduledThreadPool(NUM_THREADS);

  /**
   * A collection of running timeout tasks encapsulating the execution of timeout actions. The keys are timeout action names.
   */
  private final ConcurrentMap<String, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

  /**
   * Starts the provided timeout action.
   * <p>
   * The action name must be unique, no timeout action with the same must have been started before without being stopped.
   *
   * @param actionName Name of the timeout action.
   * @param delayInMs  Delay in milliseconds.
   * @param task       The task to execute.
   * @throws IllegalArgumentException If two timeout actions with the same name have been started without being stopped.
   */
  @Trace(name = MethodName.START)
  @Log(name = MethodName.START)
  public void start(String actionName, Number delayInMs, Runnable task) throws IllegalArgumentException {
    // Ensure unique timeout action names
    if (timeoutTasks.containsKey(actionName)) {
      throw new IllegalArgumentException("Duplicate timeout action name '%s'".formatted(actionName));
    }

    // Schedule at an interval
    final var future = timeoutTaskScheduler.scheduleWithFixedDelay(task, delayInMs.intValue(), delayInMs.intValue(), TimeUnit.MILLISECONDS);

    // Keep the task
    timeoutTasks.put(actionName, future);
  }

  /**
   * Stops a timeout action with the provided name. The encapsulating task will be cancelled immediately.
   * <p>
   * A timeout action with the provided name must have been started.
   *
   * @param actionName Name of action to stop.
   * @throws IllegalArgumentException If not exactly one timeout action was found with the provided name.
   */
  @Trace(name = MethodName.STOP)
  @Log(name = MethodName.STOP)
  public void stop(String actionName) throws IllegalArgumentException {
    // Retrieve the timeout task
    final var timeoutTasksWithName = timeoutTasks.entrySet().stream()
        .filter(entry -> entry.getKey().equals(actionName))
        .map(Map.Entry::getValue)
        .toList();

    if (timeoutTasksWithName.size() != 1) {
      throw new IllegalArgumentException("Expected exactly one timeout action with the name '%s'".formatted(actionName));
    }

    final var timeoutTask = timeoutTasksWithName.getFirst();

    // Cancel the task
    timeoutTask.cancel(true);

    // Remove the task
    timeoutTasks.remove(actionName);
  }

  /**
   * Stops all timeout actions.
   */
  @Trace(name = MethodName.STOP_ALL)
  @Log(name = MethodName.STOP_ALL)
  public void stopAll() {
    // Cancel all tasks
    timeoutTasks.values()
        .forEach(future -> future.cancel(true));

    // And clear
    timeoutTasks.clear();
  }
}
