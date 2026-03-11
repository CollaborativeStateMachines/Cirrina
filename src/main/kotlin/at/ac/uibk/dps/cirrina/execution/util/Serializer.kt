package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language

object Serializer {
  private val fory: ThreadSafeFory =
    Fory.builder()
      .withLanguage(Language.JAVA)
      .withAsyncCompilation(true)
      .buildThreadSafeFory()
      .apply {
        register(Event::class.java)
        register(Csml.EventChannel::class.java)
        register(ContextVariable::class.java)

        ensureSerializersCompiled()
      }

  fun serialize(obj: Any): ByteArray {
    if (obj is Event && obj.data.any { it.isLazy }) {
      error("event '${obj.topic}' has unevaluated data")
    }
    return fory.serialize(obj)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> deserialize(data: ByteArray): T {
    return fory.deserialize(data) as T
  }
}
