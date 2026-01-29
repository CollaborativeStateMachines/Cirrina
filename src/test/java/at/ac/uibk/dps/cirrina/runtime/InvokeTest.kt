package at.ac.uibk.dps.cirrina.runtime

import InMemoryEventHandler
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.GLOBAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.PERIPHERAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockHttpServer
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeout

class InvokeTest {

  @Test
  fun testInvokeExecute() {
    assertTimeout(Duration.ofSeconds(5)) {
      assertDoesNotThrow {
        val eventHandler =
          InMemoryEventHandler().apply {
            subscribe(GLOBAL_SOURCE)
            subscribe(PERIPHERAL_SOURCE)
          }
        val context = InMemoryContext()

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
              .testModule(TestModule(eventHandler, context, selector, DefaultDescriptions.invoke))
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
