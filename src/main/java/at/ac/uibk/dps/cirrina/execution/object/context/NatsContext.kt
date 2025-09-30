package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ValueExchange
import io.nats.client.*
import io.nats.client.api.KeyValueConfiguration
import java.io.IOException
import java.util.concurrent.CountDownLatch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * A persistent context stored in a NATS key-value bucket.
 *
 * Bucket is expected to exist already, but if missing, it is created.
 *
 * @param isLocal true if this context is local, false otherwise
 * @param natsUrl NATS server URL
 * @param bucketName name of the key-value bucket
 * @param deleteBucket if true, the bucket will be deleted upon closing the context
 */
class NatsContext(
  isLocal: Boolean,
  private val natsUrl: String,
  private val bucketName: String,
  private val deleteBucket: Boolean = false,
) : Context(isLocal), AutoCloseable {

  companion object {
    private val logger: Logger = LogManager.getLogger()
  }

  // NATS connection can be null if not connected
  private var connection: Connection? = null

  // NATs key-value can be null if not connected
  private var keyValue: KeyValue? = null

  // Lock for the connection and key-value
  private val lock = Any()

  // Latch for initial connection
  private val connectedLatch = CountDownLatch(1)

  init {
    val options =
      Options.Builder()
        .server(natsUrl)
        .connectionListener(
          object : ConnectionListener {
            override fun connectionEvent(conn: Connection, type: ConnectionListener.Events?) {
              when (type) {
                ConnectionListener.Events.CONNECTED,
                ConnectionListener.Events.RECONNECTED -> {
                  synchronized(lock) {
                    try {
                      keyValue = getOrCreateBucket(conn)
                      connection = conn
                    } catch (e: Exception) {
                      logger.error("Failed to setup bucket", e)
                    } finally {
                      connectedLatch.countDown()
                    }
                  }
                  logger.info("(Re)connected to NATS for persistent context")
                }

                ConnectionListener.Events.DISCONNECTED -> {
                  synchronized(lock) {
                    connection = null
                    keyValue = null
                  }
                  logger.warn("NATS disconnected for persistent context")
                }

                else -> {}
              }
            }
          }
        )
        .errorListener(
          object : ErrorListener {
            override fun errorOccurred(conn: Connection?, error: String?) {
              logger.error("NATS error: $error")
            }

            override fun exceptionOccurred(conn: Connection?, e: Exception?) {
              logger.error("NATS exception", e)
            }
          }
        )
        .build()

    try {
      Nats.connectAsynchronously(options, true)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.error("Interrupted while initiating NATS connection, will never connect", e)
    }
  }

  // Creates a bucket if it does not exist and returns the created bucket.
  private fun getOrCreateBucket(conn: Connection): KeyValue =
    runCatching {
        conn.keyValueManagement().apply {
          if (!bucketNames.contains(bucketName)) {
            logger.warn("Bucket '$bucketName' does not exist, creating it")
            create(KeyValueConfiguration.Builder().name(bucketName).build())
          }
        }
        conn.keyValue(bucketName)
      }
      .getOrElse { e ->
        throw IOException("Failed to create or retrieve bucket '$bucketName': ${e.message}", e)
      }

  private fun toBytes(value: Any?): ByteArray = ValueExchange(value).toBytes()

  private fun fromBytes(bytes: ByteArray): Any = ValueExchange.fromBytes(bytes).value

  /**
   * Retrieve a context variable.
   *
   * @param name name of the context variable
   * @return the retrieved context variable
   * @throws IOException if the variable does not exist
   * @throws IOException if the variable could not be retrieved
   */
  override fun get(name: String): Any =
    synchronized(lock) {
      runCatching {
          val kv = keyValue ?: throw IOException("Not connected to NATS for persistent context")
          if (!kv.keys().contains(name)) {
            throw IOException("A variable with the name '$name' does not exist")
          }

          val entry = kv.get(name)
          fromBytes(entry.value)
        }
        .getOrElse { e -> throw IOException("Failed to retrieve variable '$name'", e) }
    }

  /**
   * Creates a new context variable.
   *
   * @param name name of the context variable
   * @param value value of the context variable
   * @return byte size of stored data
   * @throws IOException if a variable with the same name already exists
   * @throws IOException if the variable could not be created
   */
  override fun create(name: String, value: Any?): Int =
    synchronized(lock) {
      runCatching {
          val kv = keyValue ?: throw IOException("Not connected to NATS for persistent context")
          if (kv.keys().contains(name)) {
            throw IOException("A variable with the name '$name' already exists")
          }

          val data = toBytes(value)
          kv.create(name, data)
          data.size
        }
        .getOrElse { e -> throw IOException("Failed to create variable '$name'", e) }
    }

  /**
   * Assigns a new value to an existing context variable.
   *
   * @param name name of the context variable
   * @param value new value of the context variable
   * @return byte size of stored data
   * @throws IOException if the variable does not exist
   * @throws IOException if the variable could not be assigned
   */
  override fun assign(name: String, value: Any?): Int =
    synchronized(lock) {
      runCatching {
          val kv = keyValue ?: throw IOException("Not connected to NATS for persistent context")
          if (!kv.keys().contains(name)) {
            throw IOException("A variable with the name '$name' does not exist")
          }

          val data = toBytes(value)
          kv.put(name, data)
          data.size
        }
        .getOrElse { e -> throw IOException("Failed to assign variable '$name'", e) }
    }

  /**
   * Deletes a context variable.
   *
   * @param name name of the context variable
   * @throws IOException if the variable does not exist
   * @throws IOException if the variable could not be deleted
   */
  override fun delete(name: String) {
    synchronized(lock) {
      runCatching {
          val kv = keyValue ?: throw IOException("Not connected to NATS for persistent context")
          if (!kv.keys().contains(name)) {
            throw IOException("A variable with the name '$name' does not exist")
          }

          kv.delete(name)
        }
        .onFailure { e -> throw IOException("Failed to delete variable '$name'", e) }
    }
  }

  /** Returns all context variables. */
  override fun getAll(): List<ContextVariable> =
    synchronized(lock) {
      runCatching {
          val kv = keyValue ?: throw IOException("Not connected to NATS for persistent context")
          kv.keys().map { key ->
            val entry = kv.get(key)
            ContextVariable(entry.key, entry.value)
          }
        }
        .getOrElse { e -> throw IOException("Failed to retrieve variables from context", e) }
    }

  override fun close() {
    synchronized(lock) {
      runCatching {
          connection?.let { conn ->
            if (deleteBucket) {
              conn.keyValueManagement().delete(bucketName)
            }
            conn.close()
          }
        }
        .onFailure { e -> throw IOException("Failed to close NATS persistent context", e) }
    }
  }

  /**
   * Waits until the initial connection is established or the timeout expires.
   *
   * @param timeoutMs maximum time to wait in milliseconds
   * @return true if connection was established, false otherwise
   */
  fun awaitInitialConnection(timeoutMs: Long = 5000): Boolean =
    connectedLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
}
