package at.ac.uibk.dps.cirrina.execution.`object`.context

/** Abstract class for contexts. */
abstract class Context() : AutoCloseable {

  /**
   * Checks if a variable exists.
   *
   * @return true if the variable exists, false otherwise.
   * @throws Exception if an internal error occurs.
   */
  abstract fun has(name: String): Boolean

  /**
   * Retrieves a variable value.
   *
   * @return the value of the variable.
   * @throws Exception if the variable does not exist or if an internal error occurs.
   */
  abstract fun get(name: String): Any?

  /**
   * Creates a new variable.
   *
   * @return the size of the value.
   * @throws Exception if the variable already exists or if an internal error occurs.
   */
  abstract fun create(name: String, value: Any?): Int

  /**
   * Assigns a value to an existing variable.
   *
   * @return the size of the value.
   * @throws Exception if the variable does not exist or if an internal error occurs.
   */
  abstract fun assign(name: String, value: Any?): Int

  /**
   * Deletes a variable.
   *
   * @throws Exception if the variable does not exist or if an internal error occurs.
   */
  abstract fun delete(name: String)

  /**
   * Deletes all variables.
   *
   * @throws Exception if the variable does not exist or if an internal error occurs.
   */
  abstract fun deleteAll()

  /**
   * Returns all variables.
   *
   * @return a list of all variables.
   * @throws Exception if an internal error occurs.
   */
  abstract fun getAll(): List<ContextVariable>
}
