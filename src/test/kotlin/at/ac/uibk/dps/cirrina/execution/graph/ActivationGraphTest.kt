package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActivationGraphTest {
  @Test
  fun test() {
    val eventHandler = EventHandler()
    val context = ContextInMemory()

    val runtime =
      DaggerTestComponent.builder()
        .testModule(
          TestModule(
            eventHandler,
            context,
            DefaultDescriptions.events,
            listOf("one", "two", "three", "four"),
          )
        )
        .build()
        .runtime()

    val graph = ActivationGraph.create(runtime.instances.values)

    graph.vertexSet().run {
      val expected =
        listOf(
          "one",
          "two",
          "three",
          "four",
          "one.0@nestedStateMachine2",
          "one.1@nestedStateMachine1",
        )

      assertTrue(containsAll(expected) && expected.containsAll(this))
    }

    graph.edgeSet().run {
      val expected =
        listOf(
          ActivationGraph.Activation("one", "two", EventChannel.GLOBAL, "e3"),
          ActivationGraph.Activation("one", "three", EventChannel.GLOBAL, "e5"),
          ActivationGraph.Activation("one", "four", EventChannel.GLOBAL, "e3"),
          ActivationGraph.Activation("one", "four", EventChannel.GLOBAL, "e5"),
          ActivationGraph.Activation("two", "one", EventChannel.EXTERNAL, "e4"),
        )

      assertTrue(containsAll(expected) && expected.containsAll(this))
    }
  }
}
