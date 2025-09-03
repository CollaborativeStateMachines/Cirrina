package at.ac.uibk.dps.cirrina.runtime;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.csml.description.JobDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.execution.object.expression.ExpressionBuilder;
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector;
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder;
import at.ac.uibk.dps.cirrina.runtime.job.Job;
import at.ac.uibk.dps.cirrina.runtime.job.JobListener;
import at.ac.uibk.dps.cirrina.runtime.job.JobMonitor;
import at.ac.uibk.dps.cirrina.utils.Time;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.opentelemetry.api.OpenTelemetry;
import java.io.IOException;
import java.util.List;
import org.apache.curator.framework.CuratorFramework;

/**
 * Online runtime, a runtime system implementation that is meant to be connected to a message queue, key-value store and coordination system
 * to allow for a distributed deployment of one or several runtime systems.
 * <p>
 * StateClass machine instantiation is triggered based on jobs.
 */
public class OnlineRuntime extends Runtime implements JobListener {

  /**
   * Start time.
   */
  private final double startTime = Time.timeInMillisecondsSinceStart();

  /**
   * Whether to delete a job when consumed.
   */
  private final boolean deleteJob;

  /**
   * Job monitor, using ZooKeeper to monitor new jobs.
   */
  private final JobMonitor jobMonitor;

  /**
   * Jobs to start in the future.
   */
  private final Multimap<Double, JobDescription> futureJobs = HashMultimap.create();

  /**
   * Initializes this online runtime instance.
   *
   * @param name              Name.
   * @param eventHandler      Event handler.
   * @param persistentContext Persistent context.
   * @param openTelemetry     OpenTelemetry.
   * @param curatorFramework  CuratorFramework.
   * @param deleteJob         Delete job when consumed.
   */
  public OnlineRuntime(
    String name,
    EventHandler eventHandler,
    Context persistentContext,
    OpenTelemetry openTelemetry,
    CuratorFramework curatorFramework,
    boolean deleteJob
  ) {
    super(name, eventHandler, persistentContext, openTelemetry);
    this.deleteJob = deleteJob;

    // Create a job monitor
    this.jobMonitor = new JobMonitor(curatorFramework, this);
  }

  /**
   * Called upon the arrival of a new job, will instantiate a new state machine if capable and appropriate.
   *
   * @param job New job.
   * @throws UnsupportedOperationException If the state machine is not known or abstract.
   */
  @Override
  public void newJob(Job job) throws UnsupportedOperationException {
    // TODO: Add additional conditions/rules

    final var jobDescriptionRuntimeName = job.getJobDescription().getRuntimeName();

    if (!name.equals(jobDescriptionRuntimeName)) {
      logger.info(
        "Found job for runtime '%s' which does not match this runtime's name '%s', ignoring".formatted(
          jobDescriptionRuntimeName,
          name
        )
      );

      return;
    }

    try {
      // Attempt to lock the job
      job.lock();

      // It is possible that it has been removed in the meantime
      if (job.exists()) {
        // Acquire the job description
        final var jobDescription = job.getJobDescription();

        // Schedule job
        synchronized (futureJobs) {
          logger.info(
            "Found a job for {} it is {} now",
            jobDescription.getStartTime(),
            Time.timeInMillisecondsSinceStart()
          );

          futureJobs.put(jobDescription.getStartTime(), jobDescription);
        }

        // Delete the job (it has been consumed)
        if (deleteJob) {
          job.delete();
        }
      }
    } finally {
      // Unlock the job
      job.unlock();
    }
  }

  private void startJob(JobDescription jobDescription) {
    // Create the collaborative state machine from the description
    final var collaborativeStateMachine = CollaborativeStateMachineClassBuilder.from(
      jobDescription.getCollaborativeStateMachine()
    ).build();

    // Acquire the service implementation selector
    final var serviceImplementationSelector = new RandomServiceImplementationSelector(
      ServiceImplementationBuilder.from(jobDescription.getServiceImplementations()).build()
    );

    // Acquire the state machine name
    final var stateMachineName = jobDescription.getStateMachineName();

    // Find the state machine by name
    final var stateMachine = collaborativeStateMachine
      .findStateMachineClassByName(stateMachineName)
      .orElseThrow(() ->
        new UnsupportedOperationException(
          "A state machine with the name '%s' does not exist in the collaborative state machine".formatted(
            stateMachineName
          )
        )
      );

    // Create persistent variables
    final var persistentContextVariables =
      collaborativeStateMachine.getPersistentContextVariables();

    persistentContextVariables.forEach(variable -> {
      try {
        logger.info("Creating persistent context variable '{}'", variable.name());

        persistentContext.create(variable.name(), variable.value());
      } catch (IOException e) {
        logger.info(
          "Did not create persistent context variable '{}', possibly already exists",
          variable.name()
        );
      }
    });

    // Create instances, newInstances will also instantiate nested state machines
    final var instanceIds = newInstances(
      List.of(stateMachine),
      serviceImplementationSelector,
      null,
      jobDescription.getEndTime()
    );

    // Assign local data from the job description if the job description contains any local data. Assign to the parent and nested state machines
    if (!jobDescription.getLocalData().isEmpty()) {
      for (final var instanceId : instanceIds) {
        final var stateMachineInstance = findInstance(instanceId).orElseThrow(() ->
          new UnsupportedOperationException(
            "State machine '%s' with id '%s' was not instantiated.".formatted(
              stateMachine.getName(),
              instanceId
            )
          )
        );

        for (final var localData : jobDescription.getLocalData().entrySet()) {
          try {
            // Assign local data entry, evaluate the value as an expression
            final var valueExpression = ExpressionBuilder.from(localData.getValue()).build();

            stateMachineInstance
              .getExtent()
              .setOrCreate(
                localData.getKey(),
                valueExpression.execute(stateMachineInstance.getExtent())
              );
          } catch (IOException | IllegalArgumentException e) {
            throw new UnsupportedOperationException(
              "Could not assign value '%s' to local data variable '%s'".formatted(
                localData.getKey(),
                localData.getValue()
              ),
              e
            );
          }
        }
      }
    }
  }

  /**
   * Run until shut down.
   */
  public void run() {
    final var SLEEP_TIME_IN_MS = 1000;

    try {
      while (!isShutdown()) {
        // Get the current time
        final var currentTime = Time.timeInMillisecondsSinceStart();

        // Collect the keys of the entries that need to be processed and removed
        synchronized (futureJobs) {
          futureJobs
            .entries()
            .removeIf(entry -> {
              final var startTime = entry.getKey();

              if (startTime < currentTime) {
                logger.info("Starting job ({}) at time: {}", startTime, currentTime);

                startJob(entry.getValue());
                return true;
              }
              return false;
            });
        }

        // Sleep
        Thread.sleep(SLEEP_TIME_IN_MS);
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
