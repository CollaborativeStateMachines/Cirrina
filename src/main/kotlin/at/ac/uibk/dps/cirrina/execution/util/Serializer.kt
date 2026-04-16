package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.spec.ContextVariable
import at.ac.uibk.dps.cirrina.spec.Event
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

object Serializer {
  private val fory: ThreadSafeFory =
    Fory.builder().withLanguage(Language.JAVA).buildThreadSafeFory().apply {
      register(Event::class.java)
      register(Csml.EventChannel::class.java)
      register(ContextVariable::class.java)
    }

  private val threadBuffer = ThreadLocal.withInitial { MemoryBuffer.newHeapBuffer(1024) }

  fun serialize(obj: Any): ByteArray {
    val buffer = threadBuffer.get()
    buffer.writerIndex(0)

    fory.serialize(buffer, obj)

    return buffer.getBytes(0, buffer.writerIndex())
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> deserialize(bytes: ByteArray): T {
    return fory.deserialize(bytes) as T
  }
}
