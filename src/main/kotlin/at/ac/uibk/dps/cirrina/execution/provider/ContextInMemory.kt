package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.spec.ContextVariable
import java.util.concurrent.ConcurrentHashMap

class ContextInMemory : Context {
  private val values = ConcurrentHashMap<String, Any?>()

  override fun has(name: String): Boolean = values.containsKey(name)

  override fun get(name: String): Any? {
    return values.getOrDefault(name, NOT_FOUND).let {
      if (it === NOT_FOUND) error("variable '$name' does not exist") else it
    }
  }

  override fun create(name: String, value: Any?): Int {
    if (values.putIfAbsent(name, value) != null) {
      error("variable '$name' already exists")
    }
    return calculateSize(value)
  }

  override fun assign(name: String, value: Any?): Int {
    if (values.replace(name, value) == null && !values.containsKey(name)) {
      error("variable '$name' does not exist")
    }
    return calculateSize(value)
  }

  override fun delete(name: String) {
    if (values.remove(name) == null) {
      error("variable '$name' does not exist")
    }
  }

  override fun deleteAll() {
    values.clear()
  }

  override fun getAll(): List<ContextVariable> =
    values.map { (key, value) -> ContextVariable(key, value) }

  override fun clear() {
    values.clear()
  }

  override fun close() {}

  private fun calculateSize(value: Any?): Int = if (value is ByteArray) value.size else 0

  companion object {
    private val NOT_FOUND = Any()
  }
}
