package at.ac.uibk.dps.cirrina.runtime.job;

/**
 * Job listener, informed when a new job is submitted.
 */
public interface JobListener {
  /**
   * Called upon the arrival of a new job, will instantiate a new state machine if capable and appropriate.
   *
   * @param job New job.
   * @throws UnsupportedOperationException If an error occurs.
   */
  void newJob(Job job) throws UnsupportedOperationException;
}
