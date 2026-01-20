package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml
import com.google.common.collect.Multimap

/**
 * A service implementation selector that randomly picks from available candidates.
 *
 * @param serviceImplementations The pool of available service implementations.
 */
class RandomServiceImplementationSelector(
  serviceImplementations: Multimap<String, ServiceImplementation>
) : ServiceImplementationSelector(serviceImplementations) {

  /**
   * Selects a random matching service implementation using idiomatic Kotlin extensions.
   *
   * @param name The name of the requested service implementation.
   * @param mode The invocation mode (LOCAL or REMOTE).
   * @return A random [ServiceImplementation], or `null` if no match is found.
   */
  override fun select(name: String, mode: Csml.InvocationMode): ServiceImplementation? {
    val candidates = serviceImplementations[name] ?: return null

    return if (mode == Csml.InvocationMode.LOCAL) {
      // Filter and pick one at random, or null if empty
      candidates.filter { it.isLocal }.randomOrNull()
    } else {
      // Pick any at random, or null if empty
      candidates.randomOrNull()
    }
  }
}
