package at.ac.uibk.dps.cirrina.execution.graph

import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import org.junit.jupiter.api.Test

class ActivationGraphTest {
  @Test
  fun test() {
    val eventHandler = EventHandler()
    val context = ContextInMemory()

    val runtime =
      DaggerTestComponent.builder()
        .testModule(TestModule(eventHandler, context, DefaultDescriptions.events))
        .build()
        .runtime()

    val exporter = DOTExporter<String, ActivationGraph.Activation>()

    exporter.setVertexAttributeProvider { vertex ->
      mapOf("label" to DefaultAttribute.createAttribute(vertex))
    }

    exporter.setEdgeAttributeProvider { edge ->
      mapOf(
        "label" to DefaultAttribute.createAttribute("${edge.event.topic} (${edge.event.channel})")
      )
    }

    exporter.exportGraph(ActivationGraph.create(runtime.instances.values), System.out)
  }
}
