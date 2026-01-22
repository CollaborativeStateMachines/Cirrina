package at.ac.uibk.dps.cirrina.execution.`object`.statemachine

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class TimeoutActionManager {

  private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  private val timeoutJobs = ConcurrentHashMap<String, Job>()

  fun start(actionName: String, delayInMs: Number, task: suspend () -> Unit) {
    check(managerScope.isActive) { "cannot start action '$actionName' on a shut down manager" }

    require(!timeoutJobs.containsKey(actionName)) { "duplicate timeout action name '$actionName'" }

    managerScope
      .launch {
        while (isActive) {
          delay(delayInMs.toLong())

          runCatching { task() }
            .onFailure { e -> logger.error(e) { "timeout action '$actionName' failed" } }
        }
      }
      .also { timeoutJobs[actionName] = it }
  }

  fun stop(actionName: String) {
    timeoutJobs.remove(actionName)?.cancel()
      ?: error("expected exactly one timeout action with the name '$actionName'")
  }

  fun stopAll() {
    timeoutJobs.values.forEach { it.cancel() }
    timeoutJobs.clear()
  }

  fun shutdown() {
    stopAll()
    managerScope.cancel()
  }
}
