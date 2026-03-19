package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

object Serializer {
  private val threadLocalBuffer = ThreadLocal.withInitial { MemoryBuffer.newHeapBuffer(1024) }

  private val fory: ThreadSafeFory =
    Fory.builder().withLanguage(Language.JAVA).buildThreadSafeFory().apply {
      register(Event::class.java)
      register(Csml.EventChannel::class.java)
      register(ContextVariable::class.java)
    }

  fun serialize(obj: Any): ByteArray {
    if (obj is Event && obj.data.any { it.isLazy }) {
      error("event '${obj.topic}' has unevaluated data")
    }

    val buffer = threadLocalBuffer.get()
    buffer.writerIndex(0)

    fory.serialize(buffer, obj)
    return buffer.getBytes(0, buffer.writerIndex())
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> deserialize(data: ByteArray): T {
    return fory.deserialize(data) as T
  }
}
