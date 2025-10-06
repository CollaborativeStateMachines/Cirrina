package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ValueExchange
import com.google.common.flogger.FluentLogger
import com.google.protobuf.ByteString
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.Client
import io.etcd.jetcd.op.Cmp
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.op.Op
import io.etcd.jetcd.options.DeleteOption
import io.etcd.jetcd.options.GetOption
import io.etcd.jetcd.options.OptionsUtil
import io.etcd.jetcd.options.PutOption
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

private class AsyncEtcdConnection(
  private val endpoints: List<String>, // Etcd endpoints
  private val retryDelayMs: Long = 1000, // Delay between retries in ms
) : Closeable {
  // Executor for retry loop.
  private val executor = Executors.newSingleThreadExecutor()

  // Controls retry loop.
  private val running = AtomicBoolean(true)

  // Future holding the Etcd client.
  private val clientFuture = CompletableFuture<Client>()

  init {
    executor.submit {
      while (running.get() && !clientFuture.isDone) {
        try {
          logger.atFiner().log("Attempting to connect to Etcd")
          clientFuture.complete(Client.builder().endpoints(*endpoints.toTypedArray()).build())
          break
        } catch (_: Exception) {
          logger.atWarning().log("Failed to connect to Etcd")
          Thread.sleep(retryDelayMs)
        }
      }
    }
  }

  // Returns the CompletableFuture that completes when the client is connected.
  fun getClientFuture(): CompletableFuture<Client> = clientFuture

  /** Closes the connection manager and the underlying Etcd client. */
  override fun close() {
    running.set(false)
    executor.shutdownNow()
    clientFuture.thenAccept { it.close() }
  }
}

class EtcdContext(isLocal: Boolean, endpoints: List<String>) : Context(isLocal), AutoCloseable {
  // Async Etcd connection manager.
  private val asyncConn = AsyncEtcdConnection(endpoints)

  // Converts value to ByteArray.
  private fun toBytes(value: Any?) = ValueExchange(value).toBytes()

  // Converts ByteArray back to value.
  private fun fromBytes(bytes: ByteArray) = ValueExchange.fromBytes(bytes).value

  // Returns the connected Etcd client or throws IOException if not connected.
  private fun client(): Client =
    asyncConn.getClientFuture().getNow(null) ?: throw IOException("Etcd client not connected")

  /**
   * Retrieves a variable by name.
   *
   * @param name name of the variable
   * @return value of the variable
   * @throws IOException if the variable does not exist or retrieval fails
   */
  override fun get(name: String): Any =
    runCatching {
        fromBytes(
          client()
            .kvClient
            .get(ByteSequence.from(name.toByteArray()))
            .get()
            .kvs
            .firstOrNull()
            ?.value
            ?.bytes ?: throw IOException("A variable with the name '$name' does not exist")
        )
      }
      .getOrElse { e -> throw IOException("Failed to retrieve variable '$name'", e) }

  /**
   * Creates a new context variable.
   *
   * The byte size is only returned for binary (byte array) data and is 0 otherwise.
   *
   * @param name name of the context variable
   * @param value value of the context variable
   * @return byte size of stored data
   * @throws IOException if a variable with the same name already exists
   */
  override fun create(name: String, value: Any?): Int =
    runCatching {
        val bytes = toBytes(value)
        val key = ByteSequence.from(name.toByteArray())
        client()
          .kvClient
          .txn()
          .If(Cmp(key, Cmp.Op.EQUAL, CmpTarget.createRevision(0)))
          .Then(Op.put(key, ByteSequence.from(bytes), PutOption.DEFAULT))
          .Else(Op.get(key, GetOption.DEFAULT))
          .commit()
          .get()
          .takeIf { it.isSucceeded }
          ?.let { bytes.size }
          ?: throw IOException("A variable with the name '$name' already exists")
      }
      .getOrElse { e -> throw IOException("Failed to create variable '$name'", e) }

  /**
   * Assigns a new value to a variable.
   *
   * @param name name of the variable
   * @param value new value
   * @return byte size of the stored data
   * @throws IOException if the assignment fails
   */
  override fun assign(name: String, value: Any?): Int =
    runCatching {
        val bytes = toBytes(value)
        val key = ByteSequence.from(name.toByteArray())
        client()
          .kvClient
          .txn()
          .If(Cmp(key, Cmp.Op.GREATER, CmpTarget.createRevision(0)))
          .Then(Op.put(key, ByteSequence.from(bytes), PutOption.DEFAULT))
          .Else(Op.get(key, GetOption.DEFAULT))
          .commit()
          .get()
          .takeIf { it.isSucceeded }
          ?.let { bytes.size }
          ?: throw IOException("A variable with the name '$name' does not exist")
      }
      .getOrElse { e -> throw IOException("Failed to assign variable '$name'", e) }

  /**
   * Deletes a variable.
   *
   * @param name name of the variable
   * @throws IOException if the variable does not exist or deletion fails
   */
  override fun delete(name: String) {
    runCatching {
        client().kvClient.delete(ByteSequence.from(name.toByteArray())).get().takeIf {
          it.deleted > 0
        } ?: throw IOException("A variable with the name '$name' does not exist")
      }
      .getOrElse { e -> throw IOException("Failed to delete variable '$name'", e) }
  }

  /**
   * Deletes all context variables.
   *
   * @throws IOException if the variable could not be deleted
   */
  override fun deleteAll() {
    runCatching {
        client()
          .kvClient
          .delete(
            ByteSequence.from(ByteString.copyFromUtf8("*")),
            DeleteOption.builder()
              .isPrefix(true)
              .withRange(OptionsUtil.prefixEndOf(ByteSequence.from(byteArrayOf())))
              .build(),
          )
          .get()
      }
      .getOrElse { e -> throw IOException("Failed to delete all variables", e) }
  }

  /**
   * Retrieves all variables.
   *
   * @return list of all context variables
   * @throws IOException if retrieval fails
   */
  override fun getAll(): List<ContextVariable> =
    runCatching {
        client()
          .kvClient
          .get(
            ByteSequence.from(ByteString.copyFromUtf8("*")),
            GetOption.builder()
              .isPrefix(true)
              .withRange(OptionsUtil.prefixEndOf(ByteSequence.from(byteArrayOf())))
              .build(),
          )
          .get()
          .kvs
          .map { ContextVariable(it.key.toString(), fromBytes(it.value.bytes)) }
      }
      .getOrElse { e -> throw IOException("Failed to retrieve all variables", e) }

  /** Closes the context and underlying Etcd connection. */
  override fun close() {
    asyncConn.close()
  }

  /**
   * Blocks until the initial connection is established or the timeout expires.
   *
   * @param timeoutMs timeout in milliseconds
   * @return true if the connection was successfully established, false if timed out
   */
  fun awaitInitialConnection(timeoutMs: Long = 5000): Boolean =
    try {
      asyncConn.getClientFuture().get(timeoutMs, TimeUnit.MILLISECONDS)
      true
    } catch (_: Exception) {
      false
    }
}
