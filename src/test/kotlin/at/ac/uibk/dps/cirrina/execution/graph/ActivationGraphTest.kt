package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.spec.Csml
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActivationGraphTest {
  @Test
  fun test() {
    val spec = Csml.create(CsmParser.parseCsml(DefaultDescriptions.events)).getOrThrow()

    val graph = ActivationGraph.create(spec.instances)

    graph.vertexSet().run {
      val expected = listOf("one", "two", "three", "four")

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
