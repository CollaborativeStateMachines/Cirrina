package at.ac.uibk.dps.cirrina.execution.`object`.event

import java.lang.AutoCloseable
import java.util.concurrent.CopyOnWriteArrayList

abstract class EventHandler : AutoCloseable {

  private val listeners = CopyOnWriteArrayList<EventListener>()

  abstract fun sendEvent(event: Event, source: String?)

  /**
   * Adds a listener to the handler. Note: We use non-nullable EventListener to avoid unnecessary
   * null checks.
   */
  fun addListener(listener: EventListener) {
    listeners.addIfAbsent(listener)
  }

  abstract fun subscribe(subject: String)

  abstract fun unsubscribe(subject: String)

  abstract fun subscribe(source: String, subject: String)

  abstract fun unsubscribe(source: String, subject: String)

  /** Propagates the event to all registered listeners. */
  protected open fun propagateEvent(event: Event) {
    // TODO: This can be async
    listeners.forEach { it.onReceiveEvent(event) }
  }
}
