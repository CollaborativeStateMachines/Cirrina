package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml
import com.google.common.collect.Multimap

class RandomServiceImplementationSelector(
  serviceImplementations: Multimap<String, ServiceImplementation>
) : ServiceImplementationSelector(serviceImplementations) {

  override fun select(name: String, mode: Csml.InvocationMode): ServiceImplementation? {
    val candidates = serviceImplementations[name] ?: return null

    return if (mode == Csml.InvocationMode.LOCAL) {
      candidates.filter { it.isLocal }.randomOrNull()
    } else {
      candidates.randomOrNull()
    }
  }
}
