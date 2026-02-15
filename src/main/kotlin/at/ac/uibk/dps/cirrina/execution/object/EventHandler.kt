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
  private val subscribers = ConcurrentHashMap<String, AdvancedSubscriber<Unit>>()

  init {
    val config =
      EnvironmentVariables.zenohEventHandlerConfigUri.get()?.let { uri ->
        Config.fromFile(File(uri)).getOrThrow()
      } ?: Config.default()

    session = Zenoh.open(config).getOrThrow()
  }

  fun bind(graph: EventGraph, names: Collection<String>, handlers: List<PropagationHandler>) {
    graph.getOutgoing(names).forEach { flow ->
      publishers.computeIfAbsent(flow.topic) { topic ->
        val publisher =
          session
            .declareAdvancedPublisher(
              KeyExpr.tryFrom("events/$topic").getOrThrow(),
              publisherDetection = true,
            )
            .getOrThrow()

        val listener =
          publisher
            .declareMatchingListener(callback = { matching -> flow.isSubscribed = matching })
            .getOrThrow()

        listeners.add(listener)

        publisher
      }
    }

    graph.getIncoming(names).forEach { flow ->
      subscribers.computeIfAbsent(flow.topic) { topic ->
        session
          .declareAdvancedSubscriber(
            KeyExpr.tryFrom("events/$topic").getOrThrow(),
            subscriberDetection = true,
            callback = { sample -> handleIncoming(sample) },
          )
          .getOrThrow()
      }
    }

    subscribers["peripheral"] =
      session
        .declareAdvancedSubscriber(
          KeyExpr.tryFrom("events/peripheral/**").getOrThrow(),
          callback = { sample -> handleIncoming(sample) },
        )
        .getOrThrow()

    handlers.forEach { this.handlers.add(it) }
  }

  fun send(event: Event) {
    if (event.channel == Csml.EventChannel.INTERNAL) return

    val publisher = publishers[event.topic] ?: error("no publisher for topic '${event.topic}'")
    val payload = ZBytes.from(EventExchange(event).toBytes())

    publisher.put(payload).onFailure { error("failed to send event '$event'") }
  }

  override fun close() {
    subscribers.values.forEach { it.close() }
    listeners.forEach { it.close() }
    publishers.values.forEach { it.close() }
    session.close()
  }

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
}
