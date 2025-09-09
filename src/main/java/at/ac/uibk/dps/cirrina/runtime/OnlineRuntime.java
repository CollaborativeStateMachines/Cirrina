package at.ac.uibk.dps.cirrina.runtime;

import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.utils.Time;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Online runtime, a runtime system implementation that is meant to be connected to a message queue, key-value store and coordination system
 * to allow for a distributed deployment of one or several runtime systems.
 * <p>
 * StateClass machine instantiation is triggered based on jobs.
 */
public class OnlineRuntime extends Runtime {

  /**
   * Start time.
   */
  private final double startTime = Time.timeInMillisecondsSinceStart();

  /**
   * Initializes this online runtime instance.
   *
   * @param name              Name.
   * @param eventHandler      Event handler.
   * @param persistentContext Persistent context.
   * @param openTelemetry     OpenTelemetry.
   * @param deleteJob         Delete job when consumed.
   */
  public OnlineRuntime(
    String name,
    EventHandler eventHandler,
    Context persistentContext,
    OpenTelemetry openTelemetry,
    boolean deleteJob
  ) {
    super(name, eventHandler, persistentContext, openTelemetry);
  }
}
