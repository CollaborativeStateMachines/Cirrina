package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import at.ac.uibk.dps.cirrina.spec.Event
import io.zenoh.Config
import io.zenoh.Session
import io.zenoh.Zenoh
import io.zenoh.annotations.Unstable
import io.zenoh.bytes.ZBytes
import io.zenoh.ext.CacheConfig
import io.zenoh.ext.HeartbeatMode
import io.zenoh.ext.HistoryConfig
import io.zenoh.ext.MissDetectionConfig
import io.zenoh.ext.RecoveryConfig
import io.zenoh.ext.RecoveryMode
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.pubsub.AdvancedPublisher
import io.zenoh.pubsub.AdvancedSubscriber
import io.zenoh.sample.Sample
import java.io.File
import java.util.concurrent.ConcurrentHashMap

typealias PropagationHandler = (Event) -> Unit

@OptIn(Unstable::class)
class EventHandler(private val handler: PropagationHandler) : AutoCloseable {
  private val session: Session

  private val publishers = ConcurrentHashMap<String, AdvancedPublisher>()
  private val subscribers = ConcurrentHashMap<String, AdvancedSubscriber<Unit>>()

  init {
    val config =
      EnvironmentVariables.zenohEventHandlerConfigUri.get()?.let { uri ->
        Config.fromFile(File(uri)).getOrThrow()
      } ?: Config.default()

    session = Zenoh.open(config).getOrThrow()

    val key = "events/peripheral/**"
    subscribers.computeIfAbsent(key) { createSubscriber(key, subscriberDetection = false) }
  }

  fun addPublishers(events: List<Event>) {
    events.forEach {
      if (it.channel == Csml.EventChannel.EXTERNAL) {
        val key = "events/${it.source}/${it.topic}"
        publishers.computeIfAbsent(key) { createPublisher(key) }
      } else {
        error("cannot create publisher for event '${it}'")
      }
    }
  }

  fun addSubscribers(instanceNames: List<String>) {
    instanceNames.forEach { name ->
      val key = "events/$name/**"
      subscribers.computeIfAbsent(key) { createSubscriber(key, subscriberDetection = true) }
    }
  }

  fun addDynamicSubscribers(instancePrefixes: List<String>) {
    instancePrefixes.forEach { prefix ->
      val key = "events/$prefix\$*/**"
      subscribers.computeIfAbsent(key) { createSubscriber(key, subscriberDetection = true) }
    }
  }

  fun emit(event: Event) {
    if (event.channel != Csml.EventChannel.EXTERNAL) return

    val key = "events/${event.source}/${event.topic}"
    val publisher = publishers[key] ?: return
    val payload = ZBytes.from(Serializer.serialize(event))

    publisher.put(payload).onFailure { error("failed to send event '$event'") }
  }

  override fun close() {
    publishers.values.forEach { it.close() }
    subscribers.values.forEach { it.close() }
    session.close()
  }

  private fun createPublisher(key: String): AdvancedPublisher =
    session
      .declareAdvancedPublisher(
        key.toKeyExpr(),
        cacheConfig = CacheConfig(CACHE_SIZE),
        sampleMissDetection =
          MissDetectionConfig(HeartbeatMode.PeriodicHeartbeat(HEARTBEAT_INTERVAL)),
        publisherDetection = true,
      )
      .getOrThrow()

  private fun createSubscriber(key: String, subscriberDetection: Boolean) =
    session
      .declareAdvancedSubscriber(
        key.toKeyExpr(),
        subscriberDetection = subscriberDetection,
        recoveryConfig = RecoveryConfig(RecoveryMode.Heartbeat),
        historyConfig = HistoryConfig(detectLatePublishers = true),
        callback = ::handleIncoming,
      )
      .getOrThrow()

  private fun handleIncoming(sample: Sample) {
    try {
      val event = Serializer.deserialize<Event>(sample.payload.toBytes())

      handler(event)
    } catch (e: Exception) {
      error("failed to handle sample from '${sample.keyExpr}': ${e.message}")
    }
  }

  private fun String.toKeyExpr() = KeyExpr.tryFrom(this).getOrThrow()

  companion object {
    const val CACHE_SIZE = 1000L
    const val HEARTBEAT_INTERVAL = 500L
  }
}
