package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import io.zenoh.Config
import io.zenoh.Zenoh
import io.zenoh.bytes.ZBytes
import io.zenoh.keyexpr.KeyExpr
import java.time.Duration
import kotlin.time.measureTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class EventsTest {

  @Test
  fun testEventsExecute() = runBlocking {
    assertDoesNotThrow {
      val context = ContextInMemory()

      val runtime =
        DaggerTestComponent.builder()
          .testModule(
            TestModule(context, DefaultDescriptions.events, listOf("one", "two", "three", "four"))
          )
          .build()
          .runtime()

      val eventJob =
        launch(Dispatchers.Default) {
          delay(1000)

          val event = Event.from(Csml.EventDescription("pe1", EventChannel.PERIPHERAL, mapOf()))
          val keyExpr = KeyExpr.tryFrom("events/peripheral/${event.topic}").getOrThrow()
          val payload = ZBytes.from(Serializer.serialize(event))

          Zenoh.open(Config.default()).getOrThrow().put(keyExpr, payload).getOrThrow()
        }

      assertTimeout(Duration.ofSeconds(10)) {
        val duration = measureTime { runtime.run() }
        println("events execution: $duration")
      }

      eventJob.join()

      assertTrue(context.get("b") as Boolean)
      assertTrue(context.get("c") as Boolean)
    }
  }
}
