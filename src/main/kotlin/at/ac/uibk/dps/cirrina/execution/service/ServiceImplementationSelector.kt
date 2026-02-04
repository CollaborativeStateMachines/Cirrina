package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml.InvocationMode

abstract class ServiceImplementationSelector(
  protected val serviceImplementations: Map<String, List<ServiceImplementation>>
) {
  abstract fun select(name: String, mode: InvocationMode): ServiceImplementation?
}

class RandomServiceImplementationSelector(
  serviceImplementations: Map<String, List<ServiceImplementation>>
) : ServiceImplementationSelector(serviceImplementations) {
  override fun select(name: String, mode: InvocationMode): ServiceImplementation? {
    val candidates = serviceImplementations[name] ?: return null

    return if (mode == InvocationMode.LOCAL) {
      candidates.filter { it.isLocal }.randomOrNull()
    } else {
      candidates.randomOrNull()
    }
  }
}
