package at.ac.uibk.dps.cirrina.execution.`object`.statemachine

import com.google.common.flogger.FluentLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

class TimeoutActionManager {

  private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  private val timeoutJobs = ConcurrentHashMap<String, Job>()

  fun start(actionName: String, delayInMs: Number, task: suspend () -> Unit) {
    check(managerScope.isActive) { "Cannot start action '$actionName' on a shut down manager" }

    require(!timeoutJobs.containsKey(actionName)) { "Duplicate timeout action name '$actionName'" }

    managerScope
      .launch {
        while (isActive) {
          delay(delayInMs.toLong())

          runCatching { task() }
            .onFailure { e ->
              logger.atWarning().withCause(e).log("timeout action '$actionName' failed")
            }
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
