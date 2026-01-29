package at.ac.uibk.dps.cirrina.execution.`object`.event

import java.lang.AutoCloseable

abstract class EventHandler : AutoCloseable {

  companion object {
    const val GLOBAL_SOURCE = "global"
    const val PERIPHERAL_SOURCE = "peripheral"
  }

  var listener: EventListener? = null

  abstract fun send(event: Event)

  abstract fun subscribe(source: String)

  abstract fun unsubscribe(source: String)

  protected open fun propagate(event: Event) {
    listener?.onReceiveEvent(event)
  }
}
