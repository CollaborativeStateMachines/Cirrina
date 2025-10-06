package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventExchange
import com.google.common.flogger.FluentLogger
import io.nats.client.*
import java.util.concurrent.CountDownLatch

private val logger: FluentLogger = FluentLogger.forEnclosingClass()

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
                      logger.atSevere().withCause(e).log("Failed to setup the NATS event handler")
                    } finally {
                      connectedLatch.countDown()
                    }
                  }
                  logger.atFine().log("Connected to the NATS server")
                }

                ConnectionListener.Events.DISCONNECTED -> {
                  synchronized(lock) {
                    connection = null
                    dispatcher = null
                  }
                  logger.atFine().log("Disconnected from the NATS server")
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
      logger
        .atSevere()
        .withCause(e)
        .log("Interrupted while initiating NATS connection, will never connect")
    }
  }

  // Handles incoming NATS messages and propagates them as Events.
  private fun handle(message: Message) {
    runCatching {
        val event = EventExchange.fromBytes(message.data).event
        propagateEvent(event)
      }
      .onFailure { e ->
        when (e) {
          is UnsupportedOperationException ->
            logger.atFiner().log("A message could not be read as an event")
          else -> logger.atWarning().log("Unexpected error while handling a message")
        }
      }
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
        Csml.EventChannel.EXTERNAL ->
          source?.let { "$it.${event.name}" }
            ?: run {
              logger.atWarning().log("Event source is null, cannot send event '${event.name}'")
              return
            }

        Csml.EventChannel.GLOBAL -> "$GLOBAL_SOURCE.${event.name}"

        else -> {
          logger.atWarning().log("Unsupported channel '${event.channel}', event not sent")
          return
        }
      }

    synchronized(lock) {
      connection?.let { conn ->
        runCatching { conn.publish(subject, EventExchange(event).toBytes()) }
          .onFailure { _ -> logger.atWarning().log("Failed to publish event '$subject'") }
      } ?: logger.atWarning().log("Not sending event, not connected to the NATS server")
    }
  }

  /**
   * Subscribes to all sources for a given event name.
   *
   * @param eventName the event name to subscribe to
   */
  override fun subscribe(eventName: String) {
    val subject = "*.$eventName"
    synchronized(lock) {
      subscriptions.add(subject)
      runCatching {
          dispatcher?.subscribe(subject)
            ?: logger.atFiner().log("Dispatcher unavailable; queued subscription: $subject")
        }
        .onFailure { _ -> logger.atWarning().log("Could not subscribe to $eventName") }
    }
  }

  /**
   * Unsubscribes from all sources for a given event name.
   *
   * @param eventName the event name to unsubscribe from
   */
  override fun unsubscribe(eventName: String) {
    val subject = "*.$eventName"
    synchronized(lock) {
      subscriptions.remove(subject)
      runCatching { dispatcher?.unsubscribe(subject) }
        .onFailure { _ -> logger.atWarning().log("Could not unsubscribe from $eventName") }
    }
  }

  /**
   * Subscribes to a specific source and event.
   *
   * @param source the source of the event
   * @param eventName the name of the event
   */
  override fun subscribe(source: String, eventName: String) {
    val subject = "$source.$eventName"
    synchronized(lock) {
      subscriptions.add(subject)
      runCatching {
          dispatcher?.subscribe(subject)
            ?: logger.atFiner().log("Dispatcher unavailable; queued subscription: $subject")
        }
        .onFailure { _ -> logger.atWarning().log("Could not subscribe to $subject") }
    }
  }

  /**
   * Unsubscribes from a specific source and event.
   *
   * @param source the source of the event
   * @param eventName the name of the event
   */
  override fun unsubscribe(source: String, eventName: String) {
    val subject = "$source.$eventName"
    synchronized(lock) {
      subscriptions.remove(subject)
      runCatching { dispatcher?.unsubscribe(subject) }
        .onFailure { _ -> logger.atWarning().log("Could not unsubscribe from $subject") }
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
        .onFailure { _ -> logger.atWarning().log("Failed to close the NATS") }
    }
  }

  /**
   * Waits until the initial connection is established or the timeout expires.
   *
   * @param timeoutMs the maximum time to wait in milliseconds
   * @return true if connection was established, false if timeout occurred
   */
  fun awaitInitialConnection(timeoutMs: Long = 5000) =
    connectedLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
}
