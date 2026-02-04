import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler

class InMemoryEventHandler : EventHandler() {
  override fun send(event: Event) {
    propagate(event)
  }

  override fun subscribe(source: String) {}

  override fun unsubscribe(source: String) {}

  override fun close() {}
}
