package at.ac.uibk.dps.cirrina.execution.`object`.context

abstract class Context(val isLocal: Boolean) : AutoCloseable {

  abstract fun has(name: String): Result<Boolean>

  abstract fun get(name: String): Result<Any?>

  abstract fun create(name: String, value: Any?): Result<Int>

  abstract fun assign(name: String, value: Any?): Result<Int>

  abstract fun delete(name: String): Result<Unit>

  abstract fun deleteAll(): Result<Unit>

  abstract fun getAll(): Result<List<ContextVariable>>
}
