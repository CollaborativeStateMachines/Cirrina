import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.io.CsmParser
import at.ac.uibk.dps.cirrina.spec.Csml
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateMachineTest {

  @Test
  fun testInputEvents() {
    val spec =
      Csml.create(CsmParser.parseCsml(DefaultDescriptions.events))
        .getOrThrow()
        .collaborativeStateMachine

    spec.stateMachines["oneMachine"]!!.run {
      val expected = listOf("pe1", "e1", "e2", "e4", "e6")

      assertTrue(inputEvents.containsAll(expected) && expected.containsAll(inputEvents))
    }

    spec.stateMachines["twoMachine"]!!.run {
      val expected = listOf("e3")

      assertTrue(inputEvents.containsAll(expected) && expected.containsAll(inputEvents))
    }

    spec.stateMachines["threeMachine"]!!.run {
      val expected = listOf("e5")

      assertTrue(inputEvents.containsAll(expected) && expected.containsAll(inputEvents))
    }

    spec.stateMachines["fourMachine"]!!.run {
      val expected = listOf("e3", "e5")

      assertTrue(inputEvents.containsAll(expected) && expected.containsAll(inputEvents))
    }
  }

  @Test
  fun testOutputEvents() {
    val spec =
      Csml.create(CsmParser.parseCsml(DefaultDescriptions.events))
        .getOrThrow()
        .collaborativeStateMachine

    spec.stateMachines["oneMachine"]!!.run {
      val outputEvents = outputEvents.map { it.topic }
      val expected = listOf("e3", "e5")

      assertTrue(outputEvents.containsAll(expected) && expected.containsAll(outputEvents))
    }

    spec.stateMachines["twoMachine"]!!.run {
      val outputEvents = outputEvents.map { it.topic }
      val expected = listOf("e4")

      assertTrue(outputEvents.containsAll(expected) && expected.containsAll(outputEvents))
    }

    spec.stateMachines["threeMachine"]!!.run {
      val outputEvents = outputEvents.map { it.topic }
      val expected = listOf("e6")

      assertTrue(outputEvents.containsAll(expected) && expected.containsAll(outputEvents))
    }

    spec.stateMachines["fourMachine"]!!.run {
      val outputEvents = outputEvents.map { it.topic }
      val expected = listOf<String>()

      assertTrue(outputEvents.containsAll(expected) && expected.containsAll(outputEvents))
    }
  }
}
