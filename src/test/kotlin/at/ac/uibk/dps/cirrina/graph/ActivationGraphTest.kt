package at.ac.uibk.dps.cirrina.graph

import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.spec.Csml
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ActivationGraphTest {
  @Test
  fun test() {
    val spec = Csml.create(CsmParser.parseCsml(DefaultDescriptions.events)).getOrThrow()

    val graph = ActivationGraph.create(spec.instances)

    graph.vertexSet().run {
      val expected = listOf("one", "two", "three", "four")

      Assertions.assertTrue(containsAll(expected) && expected.containsAll(this))
    }

    graph.edgeSet().run {
      val expected =
        listOf(
          ActivationGraph.Activation(
            "one",
            "two",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e3",
          ),
          ActivationGraph.Activation(
            "one",
            "three",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e5",
          ),
          ActivationGraph.Activation(
            "one",
            "four",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e3",
          ),
          ActivationGraph.Activation(
            "one",
            "four",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e5",
          ),
          ActivationGraph.Activation(
            "two",
            "one",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.EXTERNAL,
            "e4",
          ),
        )

      Assertions.assertTrue(containsAll(expected) && expected.containsAll(this))
    }
  }
}
