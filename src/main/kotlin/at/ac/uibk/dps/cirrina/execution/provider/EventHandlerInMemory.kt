import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler

class EventHandlerInMemory : EventHandler() {
  override fun send(event: Event) {
    propagate(event)
  }

  override fun subscribe(source: String) {}

  override fun unsubscribe(source: String) {}

  override fun register(barrier: String, member: String) {}

  override fun wait(barrier: String, n: Int) {}

  override fun close() {}
}
