import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import java.util.concurrent.ConcurrentHashMap

class InMemoryEventHandler : EventHandler() {
  private val subscriptions = ConcurrentHashMap.newKeySet<String>()

  override fun send(event: Event) {
    if (subscriptions.contains(event.source)) {
      propagate(event)
    }
  }

  override fun subscribe(source: String) {
    subscriptions.add(source)
  }

  override fun unsubscribe(source: String) {
    subscriptions.remove(source)
  }

  override fun close() {}
}
