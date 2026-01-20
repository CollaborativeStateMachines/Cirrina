package at.ac.uibk.dps.cirrina.execution.`object`.context

import java.util.concurrent.ConcurrentHashMap

/** In-memory context implementation. */
open class InMemoryContext(isLocal: Boolean) : Context(isLocal) {

  private val values = ConcurrentHashMap<String, Any?>()

  /**
   * Checks if a variable exists.
   *
   * @return true if the variable exists, false otherwise.
   * @throws Exception if an internal error occurs.
   */
  override fun has(name: String): Boolean = values.containsKey(name)

  /**
   * Retrieves a variable value.
   *
   * @return the value of the variable.
   * @throws Exception if the variable does not exist or if an internal error occurs.
   */
  override fun get(name: String): Any? =
    if (values.containsKey(name)) {
      values[name]
    } else {
      error("variable '$name' does not exist")
    }

  /**
   * Creates a new variable.
   *
   * @return the size of the value.
   * @throws Exception if the variable already exists or if an internal error occurs.
   */
  override fun create(name: String, value: Any?): Int {
    val existing = values.putIfAbsent(name, value)
    if (existing != null) {
      error("variable '$name' already exists")
    }
    return calculateSize(value)
  }

  /**
   * Assigns a value to an existing variable.
   *
   * @return the size of the value.
   * @throws Exception if the variable does not exist or if an internal error occurs.
   */
  override fun assign(name: String, value: Any?): Int {
    var keyExists = false
    values.computeIfPresent(name) { _, _ ->
      keyExists = true
      value
    }

    if (!keyExists) {
      error("variable '$name' does not exist")
    }
    return calculateSize(value)
  }

  /**
   * Deletes a variable.
   *
   * @throws Exception if the variable does not exist or if an internal error occurs.
   */
  override fun delete(name: String) {
    if (values.remove(name) == null) {
      error("variable '$name' does not exist")
    }
  }

  /**
   * Deletes all variables.
   *
   * @throws Exception if the variable does not exist or if an internal error occurs.
   */
  override fun deleteAll() {
    values.clear()
  }

  /**
   * Returns all variables.
   *
   * @return a list of all variables.
   * @throws Exception if an internal error occurs.
   */
  override fun getAll(): List<ContextVariable> =
    values.map { (key, value) -> ContextVariable.eager(key, value) }

  /** Closes the context. */
  override fun close() {}

  private fun calculateSize(value: Any?): Int = if (value is ByteArray) value.size else 0
}
