package at.ac.uibk.dps.cirrina.execution.`object`.context

import java.util.concurrent.ConcurrentHashMap

open class InMemoryContext(isLocal: Boolean) : Context(isLocal) {

  private val values = ConcurrentHashMap<String, Any?>()

  override fun has(name: String): Result<Boolean> = Result.success(values.containsKey(name))

  override fun get(name: String): Result<Any?> =
    values[name]?.let { Result.success(it) }
      ?: Result.failure(NoSuchElementException("variable '$name' does not exist"))

  override fun create(name: String, value: Any?): Result<Int> {
    val existing = values.putIfAbsent(name, value)
    return if (existing == null) {
      Result.success(calculateSize(value))
    } else {
      Result.failure(IllegalStateException("variable '$name' already exists"))
    }
  }

  override fun assign(name: String, value: Any?): Result<Int> {
    var keyExists = false
    values.computeIfPresent(name) { _, _ ->
      keyExists = true
      value
    }

    return if (keyExists) {
      Result.success(calculateSize(value))
    } else {
      Result.failure(NoSuchElementException("variable '$name' does not exist"))
    }
  }

  override fun delete(name: String): Result<Unit> =
    if (values.remove(name) != null) {
      Result.success(Unit)
    } else {
      Result.failure(NoSuchElementException("variable '$name' does not exist"))
    }

  override fun deleteAll(): Result<Unit> {
    values.clear()
    return Result.success(Unit)
  }

  override fun getAll(): Result<List<ContextVariable>> =
    Result.success(values.map { (key, value) -> ContextVariable.eager(key, value) })

  override fun close() {}

  private fun calculateSize(value: Any?): Int = if (value is ByteArray) value.size else 0
}
