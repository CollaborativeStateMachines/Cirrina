package at.ac.uibk.dps.cirrina.classes.statemachine

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.io.parsing.CsmParser
import kotlin.io.path.Path
import kotlin.io.path.pathString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StateMachineClassTest {

  private lateinit var stateMachineClass: StateMachineClass

  @BeforeAll
  fun setUp() {
    assertDoesNotThrow {
      stateMachineClass =
        CollaborativeStateMachineClassBuilder.from(
            CsmParser.parseCsml(Path(DefaultDescriptions.complete).pathString)
          )
          .build()
          .findStateMachineClassByName("stateMachine1")
          .get()
    }
  }

  @Test
  fun testGetName() {
    assertEquals("stateMachine1", stateMachineClass.name)
  }

  @Test
  fun testGetHandledEvents() {
    assertEquals(listOf("e1", "e2", "e3", "e4"), stateMachineClass.inputEvents)
  }

  @Test
  fun testGetRaisedEvents() {
    assertEquals(listOf("e1", "e2", "e3", "e4"), stateMachineClass.outputEvents.map { it.name })
  }

  @Test
  fun testGetStateByName() {
    assertDoesNotThrow { stateMachineClass.findStateClassByName("state1") }
    assertDoesNotThrow { stateMachineClass.findStateClassByName("state2") }
    assertTrue(stateMachineClass.findStateClassByName("nonExisting").isEmpty)
  }

  @Test
  fun testGetActionByName() {
    assertDoesNotThrow { stateMachineClass.findStateClassByName("action1") }
    assertTrue(stateMachineClass.findStateClassByName("nonExisting").isEmpty)
  }

  @Test
  fun testFindStateByName() {
    assertDoesNotThrow {
      assertEquals("a", stateMachineClass.findStateClassByName("a").get().name)
      assertFalse(stateMachineClass.findStateClassByName("nonExisting").isPresent)
    }
  }

  @Test
  fun testFindTransitionByEventName() {
    assertDoesNotThrow {
      val stateA = stateMachineClass.findStateClassByName("a").get()
      val transitions = stateMachineClass.findOnTransitionsFromStateByEventName(stateA, "e1")

      assertEquals(1, transitions.size)
      assertEquals("b", transitions.first().targetStateName.get())

      assertEquals(
        0,
        stateMachineClass.findOnTransitionsFromStateByEventName(stateA, "nonExisting").size,
      )
    }
  }

  @Test
  fun testToString() {
    assertEquals("stateMachine1", stateMachineClass.toString())
  }
}
