package at.ac.uibk.dps.cirrina.execution.`object`.event

import java.lang.AutoCloseable

abstract class EventHandler : AutoCloseable {
  private val listeners: MutableList<EventListener?> = ArrayList()
  private val lock = Any()

  abstract fun sendEvent(event: Event, source: String?)

  fun addListener(listener: EventListener?) {
    synchronized(lock) { listeners.add(listener) }
  }

  abstract fun subscribe(subject: String)

  abstract fun unsubscribe(subject: String)

  abstract fun subscribe(source: String, subject: String)

  abstract fun unsubscribe(source: String, subject: String)

  protected open fun propagateEvent(event: Event) {
    synchronized(lock) {
      listeners.removeIf { eventListener: EventListener? -> !eventListener!!.onReceiveEvent(event) }
    }
  }
}
