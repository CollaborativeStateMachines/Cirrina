package at.ac.uibk.dps.cirrina.execution.`object`

import java.lang.AutoCloseable
import java.util.concurrent.CopyOnWriteArrayList

typealias PropagationHandler = (Event) -> Unit

abstract class EventHandler() : AutoCloseable {

  companion object {
    const val GLOBAL_SOURCE = "global"
    const val PERIPHERAL_SOURCE = "peripheral"
  }

  private val handlers: CopyOnWriteArrayList<PropagationHandler> = CopyOnWriteArrayList()

  abstract fun send(event: Event)

  abstract fun subscribe(source: String)

  abstract fun unsubscribe(source: String)

  abstract fun register(group: String, member: String)

  abstract fun wait(group: String, parties: Int)

  fun registerHandler(handler: PropagationHandler) {
    handlers.add(handler)
  }

  protected fun propagate(event: Event) {
    handlers.forEach { it(event) }
  }
}
