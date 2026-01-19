package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable

abstract class ServiceImplementation(val name: String, val isLocal: Boolean) {

  abstract suspend fun invoke(
    input: List<ContextVariable>,
    id: String,
  ): Result<List<ContextVariable>>

  abstract val informationString: String
}
