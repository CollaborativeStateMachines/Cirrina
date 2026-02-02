package at.ac.uibk.dps.cirrina.classes.statemachine

import org.junit.jupiter.api.Assertions.*


/*@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StateMachineClassTest {

  private lateinit var stateMachineClass: StateMachineClass

  @BeforeAll
  fun setUp() {
    assertDoesNotThrow {
      stateMachineClass =
        CollaborativeStateMachineClassBuilder.from(
            CsmParser.parseCsml(DefaultDescriptions.complete)
          )
          .build()
          .getOrThrow()
          .findStateMachineClassByName("completeStateMachine")!!
    }
  }

  @Test
  fun testGetName() {
    assertEquals("completeStateMachine", stateMachineClass.name)
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
    assertDoesNotThrow { stateMachineClass.getStateClassByName("state1") }
    assertDoesNotThrow { stateMachineClass.getStateClassByName("state2") }
    assertNull(stateMachineClass.getStateClassByName("nonExisting"))
  }

  @Test
  fun testGetActionByName() {
    assertDoesNotThrow { stateMachineClass.getStateClassByName("action1") }
    assertNull(stateMachineClass.getStateClassByName("nonExisting"))
  }

  @Test
  fun testFindStateByName() {
    assertDoesNotThrow {
      assertEquals("a", stateMachineClass.getStateClassByName("a")?.name)
      assertNull(stateMachineClass.getStateClassByName("nonExisting"))
    }
  }

  @Test
  fun testFindTransitionByEventName() {
    assertDoesNotThrow {
      val stateA = stateMachineClass.getStateClassByName("a")!!
      val transitions = stateMachineClass.getOnTransitionsFromStateByEventName(stateA, "e1")

      assertEquals(1, transitions.size)
      assertEquals("b", transitions.first().to)

      assertEquals(
        0,
        stateMachineClass.getOnTransitionsFromStateByEventName(stateA, "nonExisting").size,
      )
    }
  }
}*/
