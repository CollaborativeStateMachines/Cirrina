package at.ac.uibk.dps.cirrina.execution.`object`.context

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory context backed by a concurrent hash map.
 *
 * @param isLocal true if this context is local, otherwise false
 */
open class InMemoryContext(isLocal: Boolean) : Context(isLocal) {

  private val values: MutableMap<String, Any?> = ConcurrentHashMap()

  /**
   * Retrieve a context variable.
   *
   * @param name name of the context variable
   * @return the retrieved context variable
   * @throws IOException if the variable does not exist
   */
  override fun get(name: String): Any? =
    values[name] ?: throw IOException("Variable '$name' does not exist")

  /**
   * Creates a new context variable.
   *
   * The byte size is only returned for binary (byte array) data, and is 0 otherwise.
   *
   * @param name name of the context variable
   * @param value value of the context variable
   * @return byte size of stored data
   * @throws IOException if a variable with the same name already exists
   */
  override fun create(name: String, value: Any?): Int {
    if (values.containsKey(name)) {
      throw IOException("Variable '$name' already exists")
    }
    values[name] = value
    return if (value is ByteArray) value.size else 0
  }

  /**
   * Assigns a new value to an existing context variable.
   *
   * The byte size is only returned for binary (byte array) data, and is 0 otherwise.
   *
   * @param name name of the context variable
   * @param value new value of the context variable
   * @return byte size of stored data
   * @throws IOException if the variable does not exist
   */
  override fun assign(name: String, value: Any?): Int {
    if (!values.containsKey(name)) {
      throw IOException("Variable '$name' does not exist")
    }
    values[name] = value
    return if (value is ByteArray) value.size else 0
  }

  /**
   * Deletes a context variable.
   *
   * @param name name of the context variable
   * @throws IOException if the variable does not exist
   */
  override fun delete(name: String) {
    if (values.remove(name) == null) {
      throw IOException("Variable '$name' does not exist")
    }
  }

  /** Returns all context variables. */
  override fun getAll(): List<ContextVariable> =
    values.map { (key, value) -> ContextVariable(key, value) }

  override fun close() {}
}
