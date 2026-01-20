package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml
import com.google.common.collect.Multimap

/**
 * Strategy for selecting a specific service implementation from a pool of available candidates.
 *
 * @property serviceImplementations A multimap of available service implementations indexed by name.
 */
abstract class ServiceImplementationSelector(
  protected val serviceImplementations: Multimap<String, ServiceImplementation>
) {

  /**
   * Selects a matching service implementation based on the requested name and invocation mode.
   *
   * @param name The name of the requested service implementation.
   * @param mode The invocation mode (e.g., synchronous, asynchronous).
   * @return The selected [ServiceImplementation], or `null` if no match is found.
   */
  abstract fun select(name: String, mode: Csml.InvocationMode): ServiceImplementation?
}
