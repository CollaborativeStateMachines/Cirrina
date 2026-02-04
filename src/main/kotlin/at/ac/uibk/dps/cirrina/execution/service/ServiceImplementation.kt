package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml.HttpServiceImplementationBinding
import at.ac.uibk.dps.cirrina.csm.Csml.ServiceImplementationBinding
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable

abstract class ServiceImplementation(val name: String, val isLocal: Boolean) {

  abstract suspend fun invoke(input: List<ContextVariable>): List<ContextVariable>

  companion object {
    fun from(binding: ServiceImplementationBinding): Result<ServiceImplementation> = runCatching {
      when (binding) {
        is HttpServiceImplementationBinding ->
          HttpServiceImplementation(
            binding.scheme,
            binding.host,
            binding.port.toInt(),
            binding.endPoint,
            binding.method,
            binding.name,
            binding.isLocal,
          )
        else -> error("unexpected service binding type: ${binding::class.simpleName}")
      }
    }

    fun from(
      bindings: List<ServiceImplementationBinding>
    ): Result<Map<String, List<ServiceImplementation>>> = runCatching {
      bindings.map { from(it).getOrThrow() }.groupBy { it.name }
    }
  }
}
