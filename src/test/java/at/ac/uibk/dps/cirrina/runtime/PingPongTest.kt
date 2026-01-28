package at.ac.uibk.dps.cirrina.runtime

import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockPersistentContext
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class PingPongTest {

  private class SimpleEventHandler : EventHandler() {
    override fun sendEvent(event: Event) = propagateEvent(event)

    override fun close() {}

    override fun subscribe(source: String) {}

    override fun unsubscribe(source: String) {}
  }

  @Test
  fun testPingPongExecute() {
    assertTimeout(Duration.ofSeconds(5)) {
      assertDoesNotThrow {
        val eventHandler = SimpleEventHandler()
        val context =
          mockPersistentContext(
            createBlock = { create("v", 0) },
            assignBlock = { superAssign, name, value ->
              assertEquals("v", name)
              assertTrue(value is Int)
              superAssign(name, value)
            },
          )

        val selector =
          RandomServiceImplementationSelector(
            ServiceImplementationBuilder.from(emptyList()).build().getOrThrow()
          )

        val runtime =
          DaggerTestComponent.builder()
            .testModule(
              TestModule(
                eventHandler,
                context,
                selector,
                DefaultDescriptions.pingPong,
                listOf("pingStateMachine", "pongStateMachine"),
              )
            )
            .build()
            .runtime()

        val duration = measureTime { runtime.run() }
        println("pingPong execution: $duration")

        assertEquals(100_000, context.get("v"))
      }
    }
  }
}
