package at.ac.uibk.dps.cirrina.execution.`object`.event

import java.lang.AutoCloseable

abstract class EventHandler : AutoCloseable {

  var listener: EventListener? = null

  abstract fun sendEvent(event: Event, source: String?)

  abstract fun subscribe(source: String)

  abstract fun unsubscribe(source: String)

  protected open fun propagateEvent(event: Event) {
    listener?.onReceiveEvent(event)
  }
}
