package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap

class ServiceImplementationBuilder
private constructor(
  private val bindings: List<ServiceImplementationBindings.ServiceImplementationBinding>
) {

  companion object {
    fun from(
      binding: ServiceImplementationBindings.ServiceImplementationBinding
    ): ServiceImplementationBuilder = ServiceImplementationBuilder(listOf(binding))

    fun from(
      bindings: List<ServiceImplementationBindings.ServiceImplementationBinding>
    ): ServiceImplementationBuilder = ServiceImplementationBuilder(bindings)
  }

  fun build(): Result<Multimap<String, ServiceImplementation>> = runCatching {
    val services: Multimap<String, ServiceImplementation> = ArrayListMultimap.create()

    for (binding in bindings) {
      val service = buildOne(binding).getOrThrow()
      services.put(service.name, service)
    }

    services
  }

  private fun buildOne(
    binding: ServiceImplementationBindings.ServiceImplementationBinding
  ): Result<ServiceImplementation> = runCatching {
    when (binding) {
      is ServiceImplementationBindings.HttpServiceImplementationBinding -> {
        HttpServiceImplementation(
          binding.scheme,
          binding.host,
          binding.port.toInt(),
          binding.endPoint,
          binding.method,
          binding.name,
          binding.isLocal,
        )
      }
      else -> error("unexpected service binding type: ${binding.javaClass.simpleName}")
    }
  }
}
