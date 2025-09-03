package at.ac.uibk.dps.cirrina.runtime.job;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Job monitor, watches for new jobs submitted to the coordination system.
 */
public class JobMonitor implements CuratorCacheListener {

  /**
   * Job monitor logger.
   */
  private static final String JOBS_NODE = "/jobs";

  /**
   * Job monitor logger.
   */
  private static final Logger logger = LogManager.getLogger();

  /**
   * Job node pattern.
   */
  private static final Pattern JOB_NODE_PATTERN = Pattern.compile("^\\/jobs\\/(job\\d+)$");

  /**
   * Curator framework.
   */
  private final CuratorFramework curatorFramework;

  /**
   * Curator cache of the jobs node.
   */
  private final CuratorCache curatorCache;

  /**
   * Job listener.
   */
  private final JobListener jobListener;

  /**
   * Known jobs.
   */
  private final Map<String, Job> jobs = new HashMap<>();

  /**
   * Initializes this job monitor.
   * <p>
   * Initiates watching the jobs node for newly submitted jobs.
   *
   * @param curatorFramework Curator framework.
   * @param jobListener      Job listener.
   */
  public JobMonitor(CuratorFramework curatorFramework, JobListener jobListener) {
    // Construct the ZooKeeper instance
    this.curatorFramework = curatorFramework;

    this.jobListener = jobListener;

    // Create curator cache for observing the jobs node
    curatorCache = CuratorCache.builder(this.curatorFramework, JOBS_NODE).build();

    curatorCache.listenable().addListener(this);
    curatorCache.start();
  }

  /**
   * Returns the job name if the node path provided points to a valid job (has the correct naming format), otherwise empty.
   *
   * @param nodePath Node path.
   * @return Job name or empty.
   */
  private static Optional<String> getJobName(String nodePath) {
    final var matcher = JOB_NODE_PATTERN.matcher(nodePath);

    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    } else {
      return Optional.empty();
    }
  }

  private void nodeCreated(String nodePath, byte[] data) {
    getJobName(nodePath).ifPresent(jobName -> {
        // Job path already present
        if (jobs.containsKey(nodePath)) {
          return;
        }

        // Acquire the data as a string
        final var dataAsUtf8String = new String(data, StandardCharsets.UTF_8);

        try {
          // Parse the description
          final var jobDescription = new JobDescriptionParser().parse(dataAsUtf8String);

          // Create the job
          final var job = new Job(jobName, nodePath, jobDescription, curatorFramework);

          // Register as known job
          jobs.put(nodePath, job);

          // Call listener
          jobListener.newJob(job);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
          logger.error(
            "Failed to parse job description for job '{}'. Skipping this job.",
            jobName,
            e
          );

          try {
            // Delete the invalid job
            new Job(jobName, nodePath, null, curatorFramework).delete();
            logger.info("Deleted invalid job '{}'", jobName);
          } catch (Exception ex) {
            logger.error("Failed to delete invalid job '{}'", jobName, ex);
          }
        }
      });
  }

  private void nodeDeleted(String nodePath) {
    getJobName(nodePath).ifPresent(jobName -> {
        // Job path already present
        if (!jobs.containsKey(nodePath)) {
          return;
        }

        // Remove from the collection of known jobs
        jobs.remove(nodePath);
      });
  }

  /**
   * Watcher event handler.
   *
   * @param type    Event type.
   * @param oldData Previous data.
   * @param data    Current data.
   */
  @Override
  public void event(Type type, ChildData oldData, ChildData data) {
    final var jobPath = data.getPath();

    switch (type) {
      case Type.NODE_CREATED -> nodeCreated(jobPath, data.getData());
      case Type.NODE_DELETED -> nodeDeleted(jobPath);
    }
  }
}
