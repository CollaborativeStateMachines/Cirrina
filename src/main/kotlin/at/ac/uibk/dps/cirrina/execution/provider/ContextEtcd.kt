package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.util.ValueExchange
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.Client
import io.etcd.jetcd.op.Cmp
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.op.Op
import io.etcd.jetcd.options.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private class AsyncEtcdConnection(
  private val endpoints: List<String>,
  private val retryDelayMs: Long = 1000,
) : AutoCloseable {
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val clientRef = AtomicReference<Deferred<Client>>()

  init {
    clientRef.set(
      scope.async {
        while (isActive) {
          try {
            logger.debug { "attempting to connect to etcd at $endpoints" }
            return@async Client.builder().endpoints(*endpoints.toTypedArray()).build()
          } catch (e: Exception) {
            logger.warn { "failed to connect to etcd, retrying in ${retryDelayMs}ms..." }
            delay(retryDelayMs)
          }
        }
        throw CancellationException("connection manager closed")
      }
    )
  }

  suspend fun getClient(): Result<Client> = runCatching { clientRef.get().await() }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun close() {
    scope.cancel()
    clientRef.get().let { deferred ->
      if (deferred.isCompleted && !deferred.isCancelled) {
        runCatching { deferred.getCompleted().close() }
      }
    }
  }
}

class ContextEtcd(endpoints: List<String>) : Context {
  private val asyncConn = AsyncEtcdConnection(endpoints)
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  fun awaitReady(timeoutMs: Long): Result<Unit> = runBlocking {
    runCatching {
      withTimeout(timeoutMs) { asyncConn.getClient().getOrThrow() }
      Unit
    }
  }

  private suspend fun <T> withClient(operation: String, block: suspend (Client) -> T): T {
    return try {
      val client = asyncConn.getClient().getOrThrow()
      block(client)
    } catch (ex: Exception) {
      error("etcd context failure during '$operation': ${ex.message ?: "unknown error"}")
    }
  }

  private fun Any?.toBytes(): ByteArray = ValueExchange(this).toBytes()

  private fun ByteArray.fromValueBytes(): Any? = ValueExchange.fromBytes(this).value

  private fun String.toByteSequence() = ByteSequence.from(this.toByteArray(StandardCharsets.UTF_8))

  override fun has(name: String): Boolean =
    runBlocking(scope.coroutineContext) {
      withClient("has") { client ->
        val response = client.kvClient.get(name.toByteSequence()).await()
        response.count > 0L
      }
    }

  override fun get(name: String): Any? =
    runBlocking(scope.coroutineContext) {
      withClient("get") { client ->
        val response = client.kvClient.get(name.toByteSequence()).await()
        val kv = response.kvs.firstOrNull() ?: error("variable '$name' does not exist in etcd")
        kv.value.bytes.fromValueBytes()
      }
    }

  override fun create(name: String, value: Any?): Int =
    runBlocking(scope.coroutineContext) {
      withClient("create") { client ->
        val bytes = value.toBytes()
        val key = name.toByteSequence()

        val txn =
          client.kvClient
            .txn()
            .If(Cmp(key, Cmp.Op.EQUAL, CmpTarget.createRevision(0)))
            .Then(Op.put(key, ByteSequence.from(bytes), PutOption.DEFAULT))
            .commit()
            .await()

        if (!txn.isSucceeded) error("variable '$name' already exists in etcd")
        bytes.size
      }
    }

  override fun assign(name: String, value: Any?): Int =
    runBlocking(scope.coroutineContext) {
      withClient("assign") { client ->
        val bytes = value.toBytes()
        val key = name.toByteSequence()

        val txn =
          client.kvClient
            .txn()
            .If(Cmp(key, Cmp.Op.GREATER, CmpTarget.createRevision(0)))
            .Then(Op.put(key, ByteSequence.from(bytes), PutOption.DEFAULT))
            .commit()
            .await()

        if (!txn.isSucceeded) error("variable '$name' does not exist in etcd")
        bytes.size
      }
    }

  override fun delete(name: String) {
    runBlocking(scope.coroutineContext) {
      withClient("delete") { client ->
        val response = client.kvClient.delete(name.toByteSequence()).await()
        if (response.deleted == 0L) {
          error("variable '$name' does not exist in etcd")
        }
      }
    }
  }

  override fun deleteAll() {
    runBlocking(scope.coroutineContext) {
      withClient("deleteAll") { client ->
        val allKeysPrefix = ByteSequence.from(byteArrayOf(0))
        val options = DeleteOption.builder().withRange(allKeysPrefix).build()

        client.kvClient.delete(allKeysPrefix, options).await()
      }
    }
  }

  override fun getAll(): List<ContextVariable> =
    runBlocking(scope.coroutineContext) {
      withClient("getAll") { client ->
        val allKeysPrefix = ByteSequence.from(byteArrayOf(0))
        val options = GetOption.builder().withRange(allKeysPrefix).build()

        val response = client.kvClient.get(allKeysPrefix, options).await()

        response.kvs.map { kv ->
          ContextVariable.Companion.eager(
            kv.key.toString(StandardCharsets.UTF_8),
            kv.value.bytes.fromValueBytes(),
          )
        }
      }
    }

  override fun close() {
    asyncConn.close()
    scope.cancel()
  }
}
