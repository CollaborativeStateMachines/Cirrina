package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventExchange
import io.nats.client.*
import java.util.concurrent.CountDownLatch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Event handler that uses NATS for communication.
 *
 * @param natsUrl NATS server URL
 */
class NatsEventHandler(natsUrl: String) : EventHandler() {

  companion object {
    const val GLOBAL_SOURCE = "global"
    const val PERIPHERAL_SOURCE = "peripheral"
  }

  // NATS connection can be null if not connected.
  private var connection: Connection? = null

  // NATs dispatcher can be null if not connected.
  private var dispatcher: Dispatcher? = null

  // Lock for the connection and dispatcher.
  private val lock = Any()

  // Subjects subscribed to, used to re-subscribe after reconnect.
  private val subscriptions = mutableSetOf<String>()

  // Latch for initial connection.
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
                      dispatcher?.let { conn.closeDispatcher(it) }
                      dispatcher =
                        conn.createDispatcher(::handle).apply {
                          subscriptions.forEach { subject -> subscribe(subject) }
                        }
                      connection = conn
                    } catch (e: Exception) {
                      logger.error(e) { "failed to setup the nats event handler" }
                    } finally {
                      connectedLatch.countDown()
                    }
                  }
                  logger.info { "connected to the nats server" }
                }

                ConnectionListener.Events.DISCONNECTED -> {
                  synchronized(lock) {
                    connection = null
                    dispatcher = null
                  }
                  logger.info { "disconnected from the nats server" }
                }

                else -> {}
              }
            }
          }
        )
        .build()

    try {
      Nats.connectAsynchronously(options, true)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.error(e) { "interrupted while initiating nats connection, will never connect" }
    }
  }

  // Handles incoming NATS messages and propagates them as Events.
  private fun handle(message: Message) {
    runCatching {
        val event = EventExchange.fromBytes(message.data).getOrThrow().event
        propagateEvent(event)
      }
      .onFailure { e -> logger.error(e) { "unexpected error while handling a message" } }
  }

  /**
   * Sends an event through NATS.
   *
   * Only accepts global or external events. If the event channel is unsupported, the event is
   * ignored.
   *
   * @param event the Event to send
   * @param source the source of the event, used if channel is EXTERNAL
   */
  override fun sendEvent(event: Event, source: String?) {
    val subject =
      when (event.channel) {
        Csml.EventChannel.EXTERNAL -> {
          if (source == null) {
            logger.warn { "event source is null, cannot send event '${event.name}'" }
            return
          }
          "$source.${event.name}"
        }

        Csml.EventChannel.GLOBAL -> {
          if (source != null) {
            logger.warn { "source '$source' ignored for global events" }
          }
          "$GLOBAL_SOURCE.${event.name}"
        }

        else -> {
          logger.warn { "unsupported channel '${event.channel}', event not sent" }
          return
        }
      }

    synchronized(lock) {
      connection?.let { conn ->
        runCatching { conn.publish(subject, EventExchange(event).toBytes().getOrThrow()) }
          .onFailure { _ -> logger.warn { "failed to publish event '$subject'" } }
      } ?: logger.warn { "not sending event, not connected to the nats server" }
    }
  }

  /**
   * Subscribes to all events for a given event source.
   *
   * @param source the event source to subscribe to
   */
  override fun subscribe(source: String) {
    val subject = "$source.*"
    synchronized(lock) {
      subscriptions.add(subject)
      runCatching {
          dispatcher?.subscribe(subject)
            ?: logger.info { "dispatcher unavailable; queued subscription: $subject" }
        }
        .onFailure { _ -> logger.warn { "could not subscribe to $subject" } }
    }
  }

  /**
   * Unsubscribes from all events for a given event source.
   *
   * @param source the event source to unsubscribe from
   */
  override fun unsubscribe(source: String) {
    val subject = "$source.*"
    synchronized(lock) {
      subscriptions.remove(subject)
      runCatching { dispatcher?.unsubscribe(subject) }
        .onFailure { _ -> logger.warn { "could not unsubscribe from $subject" } }
    }
  }

  /**
   * Closes this handler and the underlying NATS connection and dispatcher.
   *
   * Errors are logged but not thrown.
   */
  override fun close() {
    synchronized(lock) {
      runCatching {
          dispatcher?.let { connection?.closeDispatcher(it) }
          connection?.close()
        }
        .onFailure { _ -> logger.warn { "failed to close the nats" } }
    }
  }

  /**
   * Waits until the initial connection is established or the timeout expires.
   *
   * @param timeoutMs the maximum time to wait in milliseconds
   * @return true if connection was established, false if timeout occurred
   */
  fun awaitReady(timeoutMs: Long = 5000) =
    connectedLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
}
