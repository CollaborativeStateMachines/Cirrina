package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ValueExchange
import com.google.common.flogger.FluentLogger
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.Client
import io.etcd.jetcd.op.Cmp
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.op.Op
import io.etcd.jetcd.options.*
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await // Requires kotlinx-coroutines-jdk8

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

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
            logger.atFiner().log("attempting to connect to etcd")
            return@async Client.builder().endpoints(*endpoints.toTypedArray()).build()
          } catch (ex: Exception) {
            logger.atWarning().withCause(ex).log("failed to connect to etcd, retrying...")
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
    val deferred = clientRef.get()
    if (deferred.isCompleted && !deferred.isCancelled) {
      runCatching { deferred.getCompleted().close() }
    }
  }
}

class EtcdContext(isLocal: Boolean, endpoints: List<String>) : Context(isLocal) {

  private val asyncConn = AsyncEtcdConnection(endpoints)
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  fun awaitReady(timeoutMs: Long): Result<Unit> = runBlocking {
    asyncConn
      .getClient()
      .fold(
        onSuccess = {
          try {
            withTimeout(timeoutMs) { asyncConn.getClient().getOrThrow() }
            Result.success(Unit)
          } catch (e: Exception) {
            Result.failure(e)
          }
        },
        onFailure = { Result.failure(it) },
      )
  }

  private suspend fun <T> withClient(operation: String, block: suspend (Client) -> T): T {
    return try {
      val client = asyncConn.getClient().getOrThrow()
      block(client)
    } catch (ex: Exception) {
      error("Etcd context failure during '$operation': ${ex.message ?: "Unknown error"}")
    }
  }

  private fun Any?.toBytes(): ByteArray = ValueExchange(this).toBytes()

  private fun ByteArray.fromValueBytes(): Any? = ValueExchange.fromBytes(this).value

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
        val kv = response.kvs.firstOrNull() ?: error("variable '$name' does not exist in Etcd")

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

        if (!txn.isSucceeded) error("variable '$name' already exists in Etcd")
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

        if (!txn.isSucceeded) error("variable '$name' does not exist in Etcd")
        bytes.size
      }
    }

  override fun delete(name: String) =
    runBlocking(scope.coroutineContext) {
      withClient("delete") { client ->
        val response = client.kvClient.delete(name.toByteSequence()).await()
        if (response.deleted == 0L) {
          error("variable '$name' does not exist in Etcd")
        }
      }
    }

  override fun deleteAll() =
    runBlocking(scope.coroutineContext) {
      withClient("deleteAll") { client ->
        client.kvClient
          .delete("*".toByteSequence(), DeleteOption.builder().isPrefix(true).build())
          .await()
        Unit
      }
    }

  override fun getAll(): List<ContextVariable> =
    runBlocking(scope.coroutineContext) {
      withClient("getAll") { client ->
        val response =
          client.kvClient
            .get("*".toByteSequence(), GetOption.builder().isPrefix(true).build())
            .await()

        response.kvs.map {
          ContextVariable.eager(it.key.toString(), it.value.bytes.fromValueBytes())
        }
      }
    }

  override fun close() {
    asyncConn.close()
    scope.cancel()
  }

  private fun String.toByteSequence() = ByteSequence.from(this.toByteArray())
}
