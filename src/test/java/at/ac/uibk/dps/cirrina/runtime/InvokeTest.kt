package at.ac.uibk.dps.cirrina.runtime

import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockHttpServer
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockPersistentContext
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class InvokeTest {

  private class SimpleEventHandler : EventHandler() {
    override fun sendEvent(event: Event) = propagateEvent(event)

    override fun close() {}

    override fun subscribe(source: String) {}

    override fun unsubscribe(source: String) {}
  }

  @Test
  fun testInvokeExecute() {
    assertTimeout(Duration.ofSeconds(5)) {
      assertDoesNotThrow {
        val eventHandler = SimpleEventHandler()
        val context =
          mockPersistentContext(
            createBlock = {
              create("v", 0)
              create("e", 0)
            },
            assignBlock = { superAssign, name, value ->
              assertTrue(name == "v" || name == "e")
              assertTrue(value is Int)
              superAssign(name, value)
            },
          )

        val server = mockHttpServer { input ->
          val v = input.firstOrNull { it.name == "v" } ?: error("variable 'v' not found")
          listOf(ContextVariable("v", (v.value as Int) + 1))
        }

        try {
          val service =
            ServiceImplementationBindings.HttpServiceImplementationBinding(
              "increment",
              true,
              ServiceImplementationBindings.Type.HTTP,
              "http",
              "localhost",
              8000,
              "/increment",
              ServiceImplementationBindings.HttpMethod.GET,
            )

          val selector =
            RandomServiceImplementationSelector(
              ServiceImplementationBuilder.from(listOf(service)).build().getOrThrow()
            )

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(
                  eventHandler,
                  context,
                  selector,
                  DefaultDescriptions.invoke,
                  listOf("invokeStateMachine"),
                )
              )
              .build()
              .runtime()

          val duration = measureTime { runtime.run() }
          println("invoke execution: $duration")

          assertEquals(10, context.get("v"))
        } finally {
          server.stop(1)
        }
      }
    }
  }
}
