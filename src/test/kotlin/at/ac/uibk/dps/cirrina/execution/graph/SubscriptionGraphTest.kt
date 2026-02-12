package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.graph.ActivationGraph
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.spec.Csml
import org.junit.jupiter.api.Test

class SubscriptionGraphTest {
  @Test
  fun test() {
    val spec = Csml.create(CsmParser.parseCsml(DefaultDescriptions.events)).getOrThrow()

    val graph = SubscriptionGraph.create(ActivationGraph.create(spec.instances))

    println(graph.getOutwardEdges(listOf("one", "two", "three", "four")))
    println(graph.getInwardEdges(listOf("one", "two", "three", "four")))
    println()
  }
}
