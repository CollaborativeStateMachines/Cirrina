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

  private suspend fun <T> withClient(block: suspend (Client) -> Result<T>): Result<T> =
    asyncConn.getClient().fold(onSuccess = { block(it) }, onFailure = { Result.failure(it) })

  private fun Any?.toBytes(): Result<ByteArray> = ValueExchange(this).toBytes()

  private fun ByteArray.fromValueBytes(): Any? = ValueExchange.fromBytes(this).getOrNull()?.value

  fun awaitReady(timeoutMs: Long): Result<Unit> = runBlocking {
    asyncConn
      .getClient()
      .fold(
        onSuccess = {
          try {
            withTimeout(timeoutMs) { asyncConn.getClient().getOrThrow() }
            Result.success(Unit)
          } catch (ex: TimeoutCancellationException) {
            Result.failure(RuntimeException("timed out awaiting etcd connection"))
          } catch (ex: Exception) {
            Result.failure(ex)
          }
        },
        onFailure = { Result.failure(it) },
      )
  }

  override fun get(name: String): Result<Any?> =
    runBlocking(scope.coroutineContext) {
      withClient { client ->
        val response = client.kvClient.get(name.toByteSequence()).await()
        val kv = response.kvs.firstOrNull()

        kv?.value?.bytes?.fromValueBytes()?.let { Result.success(it) }
          ?: error("variable does not exist")
      }
    }

  override fun create(name: String, value: Any?): Result<Int> =
    runBlocking(scope.coroutineContext) {
      withClient { client ->
        value.toBytes().mapCatching { bytes ->
          val key = name.toByteSequence()
          val txn =
            client.kvClient
              .txn()
              .If(Cmp(key, Cmp.Op.EQUAL, CmpTarget.createRevision(0)))
              .Then(Op.put(key, ByteSequence.from(bytes), PutOption.DEFAULT))
              .commit()
              .await()

          if (txn.isSucceeded) bytes.size else error("variable already exists")
        }
      }
    }

  override fun assign(name: String, value: Any?): Result<Int> =
    runBlocking(scope.coroutineContext) {
      withClient { client ->
        value.toBytes().mapCatching { bytes ->
          val key = name.toByteSequence()
          val txn =
            client.kvClient
              .txn()
              .If(Cmp(key, Cmp.Op.GREATER, CmpTarget.createRevision(0)))
              .Then(Op.put(key, ByteSequence.from(bytes), PutOption.DEFAULT))
              .commit()
              .await()

          if (txn.isSucceeded) bytes.size
          else throw NoSuchElementException("variable '$name' does not exist")
        }
      }
    }

  override fun delete(name: String): Result<Unit> =
    runBlocking(scope.coroutineContext) {
      withClient { client ->
        val response = client.kvClient.delete(name.toByteSequence()).await()
        if (response.deleted == 0L)
          Result.failure(NoSuchElementException("variable '$name' does not exist"))
        else Result.success(Unit)
      }
    }

  override fun deleteAll(): Result<Unit> =
    runBlocking(scope.coroutineContext) {
      withClient { client ->
        client.kvClient
          .delete("*".toByteSequence(), DeleteOption.builder().isPrefix(true).build())
          .await()
        Result.success(Unit)
      }
    }

  override fun getAll(): Result<List<ContextVariable>> =
    runBlocking(scope.coroutineContext) {
      withClient { client ->
        val response =
          client.kvClient
            .get("*".toByteSequence(), GetOption.builder().isPrefix(true).build())
            .await()

        val variables =
          response.kvs.map { ContextVariable(it.key.toString(), it.value.bytes.fromValueBytes()) }
        Result.success(variables)
      }
    }

  override fun close() {
    asyncConn.close()
    scope.cancel()
  }

  private fun String.toByteSequence() = ByteSequence.from(this.toByteArray())
}
