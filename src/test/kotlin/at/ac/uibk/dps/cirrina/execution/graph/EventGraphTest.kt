package at.ac.uibk.dps.cirrina.execution.graph

class EventGraphTest {
  /*val graph =
    EventGraph.create(
      Csml.create(CsmParser.parseCsml(DefaultDescriptions.events))
        .getOrThrow()
        .instances
        .associate { it.name to it.stateMachine },
      callback = {},
    )

  @Test
  fun testCreation() {
    graph.vertexSet().run {
      val expected = listOf("one", "two", "three", "four")

      Assertions.assertTrue(containsAll(expected) && expected.containsAll(this))
    }

    graph.edgeSet().run {
      val expected =
        listOf(
          EventGraph.Flow(
            "one",
            "two",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e3",
            false,
          ),
          EventGraph.Flow(
            "one",
            "three",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e5",
            false,
          ),
          EventGraph.Flow(
            "one",
            "four",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e3",
            false,
          ),
          EventGraph.Flow(
            "one",
            "four",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e5",
            false,
          ),
          EventGraph.Flow(
            "two",
            "one",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.EXTERNAL,
            "e4",
            false,
          ),
          EventGraph.Flow(
            "three",
            "one",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.EXTERNAL,
            "e6",
            false,
          ),
        )

      Assertions.assertTrue(containsAll(expected) && expected.containsAll(this))
    }
  }

  @Test
  fun testIncomingOutgoing() {
    graph.getIncoming(listOf("one", "two")).run {
      val expected =
        listOf(
          EventGraph.Flow(
            "one",
            "two",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e3",
            false,
          ),
          EventGraph.Flow(
            "two",
            "one",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.EXTERNAL,
            "e4",
            false,
          ),
          EventGraph.Flow(
            "three",
            "one",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.EXTERNAL,
            "e6",
            false,
          ),
        )

      Assertions.assertTrue(containsAll(expected) && expected.containsAll(this))
    }

    graph.getOutgoing(listOf("one", "two")).run {
      val expected =
        listOf(
          EventGraph.Flow(
            "one",
            "two",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e3",
            false,
          ),
          EventGraph.Flow(
            "one",
            "three",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e5",
            false,
          ),
          EventGraph.Flow(
            "one",
            "four",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e3",
            false,
          ),
          EventGraph.Flow(
            "one",
            "four",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.GLOBAL,
            "e5",
            false,
          ),
          EventGraph.Flow(
            "two",
            "one",
            at.ac.uibk.dps.cirrina.csm.Csml.EventChannel.EXTERNAL,
            "e4",
            false,
          ),
        )

      Assertions.assertTrue(containsAll(expected) && expected.containsAll(this))
    }
  }*/
}
