package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.util.EventExchange
import io.zenoh.Config
import io.zenoh.Session
import io.zenoh.Zenoh
import io.zenoh.bytes.ZBytes
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.pubsub.Subscriber
import io.zenoh.sample.Sample
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ZenohEventHandler() : EventHandler() {
  private val session: Session

  private val activeSubscriptions = ConcurrentHashMap<String, Subscriber<Unit>>()

  init {
    val config =
      EnvironmentVariables.zenohEventHandlerConfigUri.get()?.let { uri ->
        Config.fromFile(File(uri)).getOrThrow()
      } ?: Config.default()

    this.session = Zenoh.open(config).getOrThrow()
  }

  override fun send(event: Event) {
    val pathString = getZenohPath(event) ?: return

    val keyExpr = KeyExpr.tryFrom(pathString).getOrThrow()
    val bytes = EventExchange(event).toBytes()

    val payload = ZBytes.from(bytes)

    session.put(keyExpr, payload).onFailure { error("failed to send event '$event'") }
  }

  override fun subscribe(source: String) {
    val selectorString = "$source/**"

    activeSubscriptions.computeIfAbsent(selectorString) {
      val selector = KeyExpr.tryFrom(selectorString).getOrThrow()

      session
        .declareSubscriber(selector, callback = { sample: Sample -> handleIncoming(sample) })
        .getOrElse { error("failed to subscribe to '$source'") }
    }
  }

  private fun handleIncoming(sample: Sample) {
    runCatching {
        val bytes = sample.payload.toBytes()
        val event = EventExchange.fromBytes(bytes).event
        propagate(event)
      }
      .onFailure { error("failed to handle sample from '${sample.keyExpr}'") }
  }

  override fun unsubscribe(source: String) {
    val selectorString = "$source/**"
    activeSubscriptions.remove(selectorString)?.close()
  }

  private fun getZenohPath(event: Event): String? {
    return when (event.channel) {
      Csml.EventChannel.EXTERNAL -> event.source.let { "$it/${event.topic}" }
      Csml.EventChannel.GLOBAL -> "$GLOBAL_SOURCE/${event.topic}"
      else -> null
    }
  }

  override fun close() {
    activeSubscriptions.values.forEach { it.close() }
    session.close()
  }
}
