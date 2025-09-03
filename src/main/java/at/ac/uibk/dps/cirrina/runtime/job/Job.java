package at.ac.uibk.dps.cirrina.runtime.job;

import at.ac.uibk.dps.cirrina.csml.description.JobDescription;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;

/**
 * Job, represents a job submitted to the coordination system requesting the instantiation of a state machine. A job is described by means
 * of a job description.
 * <p>
 * Functionality is provided to lock and unlock the job, such that the instantiation of a state machine according to the job is
 * synchronized.
 */
public class Job {

  /**
   * The lock path format.
   */
  private static final String LOCK_PATH_FORMAT = "/locks/%s";

  /**
   * The name of the job.
   */
  private final String jobName;

  /**
   * The node path to the job.
   */
  private final String nodePath;

  /**
   * The job description.
   */
  private final JobDescription jobDescription;

  /**
   * Curator framework.
   */
  private final CuratorFramework curatorFramework;

  /**
   * Job lock, can be null in case no lock has been acquired. Will be non-null if the job has been acquired.
   */
  private InterProcessSemaphoreMutex sharedLock;

  /**
   * Initializes this job instance.
   *
   * @param jobName          Name of the job.
   * @param nodePath         Node path of the job.
   * @param jobDescription   Job description.
   * @param curatorFramework Curator framework.
   */
  public Job(
    String jobName,
    String nodePath,
    JobDescription jobDescription,
    CuratorFramework curatorFramework
  ) {
    this.jobName = jobName;
    this.nodePath = nodePath;
    this.jobDescription = jobDescription;
    this.curatorFramework = curatorFramework;
  }

  /**
   * Deletes this job.
   *
   * @throws UnsupportedOperationException If the job could not be deleted.
   */
  public void delete() throws UnsupportedOperationException {
    try {
      curatorFramework.delete().forPath(nodePath);
    } catch (Exception e) {
      throw new UnsupportedOperationException("Failed to delete job '%s'".formatted(nodePath), e);
    }
  }

  /**
   * Locks this job, will block execution until the lock can be acquired.
   * <p>
   * Every call to lock needs to be balanced with a call to unlock.
   *
   * @throws UnsupportedOperationException If a job lock was already created.
   * @throws UnsupportedOperationException If a job lock could not be acquired/created.
   */
  public void lock() throws UnsupportedOperationException {
    if (sharedLock != null) {
      throw new UnsupportedOperationException("A job lock was already created");
    }

    // Acquire a job lock
    try {
      sharedLock = new InterProcessSemaphoreMutex(
        curatorFramework,
        String.format(LOCK_PATH_FORMAT, jobName)
      );
      sharedLock.acquire();
    } catch (Exception e) {
      throw new UnsupportedOperationException("Failed to create/acquire job lock", e);
    }
  }

  /**
   * Unlocks the job.
   *
   * @throws UnsupportedOperationException If the job has not been locked.
   * @throws UnsupportedOperationException If the job could not be released.
   */
  public void unlock() throws UnsupportedOperationException {
    if (sharedLock == null) {
      throw new UnsupportedOperationException("A job lock was not created");
    }

    // Release a job lock
    try {
      sharedLock.release();
    } catch (Exception e) {
      throw new UnsupportedOperationException("Failed to release job lock", e);
    }
  }

  /**
   * Returns a flag that indicates if this job exists.
   *
   * @return True if existent, otherwise false.
   * @throws UnsupportedOperationException If the existence could not be checked.
   */
  public boolean exists() throws UnsupportedOperationException {
    try {
      return curatorFramework.checkExists().forPath(nodePath) != null;
    } catch (Exception e) {
      throw new UnsupportedOperationException(
        "Failed to check for existence of '%s'".formatted(nodePath),
        e
      );
    }
  }

  /**
   * Returns the job description.
   *
   * @return Job description.
   */
  public JobDescription getJobDescription() {
    return jobDescription;
  }
}
