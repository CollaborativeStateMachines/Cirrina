package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable

/**
 * An abstract base class for service implementations within the execution engine.
 *
 * A service implementation defines how a specific type of service (e.g., HTTP, gRPC, or local) is
 * invoked. It handles the mapping of input variables to the service's native protocol and the
 * parsing of results back into context variables.
 *
 * @property name the unique name of the service implementation.
 * @property isLocal indicates whether the service is executed locally or remotely.
 */
abstract class ServiceImplementation(val name: String, val isLocal: Boolean) {

  /**
   * Invokes the service implementation asynchronously with the provided input.
   *
   * @param input the list of context variables to be passed to the service.
   * @return a [Result] containing the list of context variables returned by the service on success,
   *   or a failure if the invocation or response parsing fails.
   */
  abstract suspend fun invoke(input: List<ContextVariable>): Result<List<ContextVariable>>
}
