package at.ac.uibk.dps.cirrina.execution.`object`.event

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventExchange
import io.nats.client.*
import java.util.concurrent.CountDownLatch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Event handler that uses NATS for communication.
 *
 * @param natsUrl the NATS server URL
 */
class NatsEventHandler(natsUrl: String) : EventHandler() {

  companion object {
    const val GLOBAL_SOURCE = "global"
    const val PERIPHERAL_SOURCE = "peripheral"
    private val logger: Logger = LogManager.getLogger()
  }

  private var connection: Connection? = null
  private var dispatcher: Dispatcher? = null
  private val lock = Any()
  private val subscriptions = mutableSetOf<String>()
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
                ConnectionListener.Events.RECONNECTED ->
                  synchronized(lock) {
                      connection = conn

                      // Close the existing dispatcher and create a new one
                      dispatcher?.let { conn.closeDispatcher(it) }
                      dispatcher = conn.createDispatcher(::handle)

                      // Subscribe to all existing subscriptions
                      subscriptions.forEach { subject ->
                        dispatcher?.subscribe(subject)
                          ?: logger.debug("Queued subscription failed unexpectedly: $subject")
                      }

                      // Signal that the initial connection has been established
                      connectedLatch.countDown()
                    }
                    .also { logger.info("(Re)connected to NATS") }

                ConnectionListener.Events.DISCONNECTED ->
                  synchronized(lock) {
                      connection = null
                      dispatcher = null
                    }
                    .also { logger.warn("NATS disconnected") }

                else -> logger.info("NATS connection event: $type")
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

  // Handles incoming NATS messages and propagates them as Events.
  private fun handle(message: Message) {
    try {
      val event = EventExchange.fromBytes(message.data).event
      propagateEvent(event)
    } catch (e: UnsupportedOperationException) {
      logger.debug("A message could not be read as an event: ${e.message}")
    } catch (e: Exception) {
      logger.error("Unexpected error handling NATS message", e)
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
              logger.warn("Event source is null, cannot send event '${event.name}'")
              return
            }

        Csml.EventChannel.GLOBAL -> "$GLOBAL_SOURCE.${event.name}"

        else -> {
          logger.warn("Unsupported channel '${event.channel}', event not sent")
          return
        }
      }

    synchronized(lock) {
      connection?.let { conn ->
        try {
          conn.publish(subject, EventExchange(event).toBytes())
        } catch (e: Exception) {
          logger.error("Failed to publish event '$subject' through NATS", e)
        }
      } ?: logger.warn("Not sending event, not connected to NATS")
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
      try {
        dispatcher?.subscribe(subject)
          ?: logger.debug("Dispatcher unavailable; queued subscription: $subject")
      } catch (e: Exception) {
        logger.error("Could not subscribe to $eventName", e)
      }
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
      try {
        dispatcher?.unsubscribe(subject)
      } catch (e: Exception) {
        logger.error("Could not unsubscribe from $eventName", e)
      }
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
      try {
        dispatcher?.subscribe(subject)
          ?: logger.debug("Dispatcher unavailable; queued subscription: $subject")
      } catch (e: Exception) {
        logger.error("Could not subscribe to $subject", e)
      }
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
      try {
        dispatcher?.unsubscribe(subject)
      } catch (e: Exception) {
        logger.error("Could not unsubscribe from $subject", e)
      }
    }
  }

  /**
   * Closes this handler and the underlying NATS connection and dispatcher.
   *
   * Errors are logged but not thrown.
   */
  override fun close() {
    synchronized(lock) {
      try {
        dispatcher?.let { connection?.closeDispatcher(it) }
        connection?.close()
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        logger.error("Failed to close NATS event handler due to interruption", e)
      } catch (e: Exception) {
        logger.error("Failed to close NATS event handler", e)
      }
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
