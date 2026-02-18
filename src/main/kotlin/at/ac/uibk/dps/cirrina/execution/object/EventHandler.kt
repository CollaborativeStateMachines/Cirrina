package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.graph.EventGraph
import at.ac.uibk.dps.cirrina.execution.util.EventExchange
import io.zenoh.Config
import io.zenoh.Session
import io.zenoh.Zenoh
import io.zenoh.annotations.Unstable
import io.zenoh.bytes.ZBytes
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.pubsub.AdvancedPublisher
import io.zenoh.pubsub.AdvancedSubscriber
import io.zenoh.pubsub.MatchingListener
import io.zenoh.sample.Sample
import java.io.File
import java.lang.AutoCloseable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

typealias PropagationHandler = (Event) -> Unit

@OptIn(Unstable::class)
class EventHandler() : AutoCloseable {
  private val handlers: CopyOnWriteArrayList<PropagationHandler> = CopyOnWriteArrayList()

  private val session: Session

  private val publishers = ConcurrentHashMap<String, AdvancedPublisher>()
  private val listeners = CopyOnWriteArrayList<MatchingListener>()
  private val subscribers = CopyOnWriteArrayList<AdvancedSubscriber<Unit>>()

  init {
    val config =
      EnvironmentVariables.zenohEventHandlerConfigUri.get()?.let { uri ->
        Config.fromFile(File(uri)).getOrThrow()
      } ?: Config.default()

    session = Zenoh.open(config).getOrThrow()
  }

  fun bind(
    graph: EventGraph,
    instanceNames: Collection<String>,
    subscribedTo: Collection<String>,
    handlers: List<PropagationHandler>,
  ) {
    this.handlers.addAll(handlers)

    // Event publishers
    graph.getOutgoing(instanceNames).forEach { flow ->
      val key = flow.event.toKey() ?: error("cannot create publisher for event '${flow.event}'")
      publishers.computeIfAbsent(key) { createPublisher(it, flow) }
    }

    // External event subscribers
    subscribedTo.forEach { name ->
      val key = "events/$name/**"
      subscribers.add(createSubscriber(key, subscriberDetection = true))
    }

    // Peripheral event subscribers
    subscribers.add(createSubscriber("events/peripheral/**", subscriberDetection = false))
  }

  fun send(event: Event) {
    val key = event.toKey() ?: return
    val publisher = publishers[key] ?: error("no publisher for topic '${key}'")
    val payload = ZBytes.from(EventExchange(event).toBytes())

    publisher.put(payload).onFailure { error("failed to send event '$event'") }
  }

  override fun close() {
    subscribers.forEach { it.close() }
    listeners.forEach { it.close() }
    publishers.values.forEach { it.close() }
    session.close()
  }

  private fun createPublisher(key: String, flow: EventGraph.Flow): AdvancedPublisher {
    val publisher =
      session.declareAdvancedPublisher(key.toKeyExpr(), publisherDetection = true).getOrThrow()

    val listener =
      publisher.declareMatchingListener(callback = { flow.isSubscribed = it }).getOrThrow()

    listeners.add(listener)
    return publisher
  }

  private fun createSubscriber(key: String, subscriberDetection: Boolean) =
    session
      .declareAdvancedSubscriber(
        key.toKeyExpr(),
        subscriberDetection = subscriberDetection,
        callback = ::handleIncoming,
      )
      .getOrThrow()

  private fun handleIncoming(sample: Sample) {
    runCatching {
        val bytes = sample.payload.toBytes()
        val event = EventExchange.fromBytes(bytes).event

        propagate(event)
      }
      .onFailure { error("failed to handle sample from '${sample.keyExpr}'") }
  }

  private fun propagate(event: Event) {
    handlers.forEach { it(event) }
  }

  private fun String.toKeyExpr() = KeyExpr.tryFrom(this).getOrThrow()

  private fun Event.toKey() =
    when (channel) {
      Csml.EventChannel.EXTERNAL -> "events/${source}/${topic}"
      else -> null
    }
}
