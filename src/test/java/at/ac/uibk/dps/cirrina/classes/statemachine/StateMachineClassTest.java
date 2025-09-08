package at.ac.uibk.dps.cirrina.classes.statemachine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions;
import at.ac.uibk.dps.cirrina.io.description.CsmlParser;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class StateMachineClassTest {

  private static StateMachineClass stateMachineClass;

  @BeforeAll
  public static void setUp() {
    var json = DefaultDescriptions.complete;

    assertDoesNotThrow(() -> {
      var collaborativeStateMachine = CollaborativeStateMachineClassBuilder.from(
        CsmlParser.parse(json)
      ).build();

      stateMachineClass = collaborativeStateMachine
        .findStateMachineClassByName("stateMachine1")
        .get();
    });
  }

  @Test
  void testGetName() {
    assertEquals(stateMachineClass.getName(), "stateMachine1");
  }

  @Test
  void testGetHandledEvents() {
    var handledEvents = stateMachineClass.getInputEvents();
    var expectedHandledEvents = List.of("e1", "e2");
    assertEquals(handledEvents, expectedHandledEvents);
  }

  @Test
  void testGetRaisedEvents() {
    var raisedEvents = stateMachineClass.getInputEvents();
    var expectedRaisedEvents = List.of("e1", "e2");
    assertEquals(raisedEvents, expectedRaisedEvents);
  }

  @Test
  void testGetStateByName() {
    assertDoesNotThrow(() -> stateMachineClass.findStateClassByName("state1"));
    assertDoesNotThrow(() -> stateMachineClass.findStateClassByName("state2"));

    assertTrue(stateMachineClass.findStateClassByName("nonExisting").isEmpty());
  }

  @Test
  void testGetActionByName() {
    assertDoesNotThrow(() -> stateMachineClass.findStateClassByName("action1"));

    assertTrue(stateMachineClass.findStateClassByName("nonExisting").isEmpty());
  }

  @Test
  void testFindStateByName() {
    assertDoesNotThrow(() -> {
      stateMachineClass.findStateClassByName("state1").get().getName().equals("state1");

      assertFalse(stateMachineClass.findStateClassByName("nonExisting").isPresent());
    });
  }

  @Test
  void testFindTransitionByEventName() {
    assertDoesNotThrow(() -> {
      var t = stateMachineClass.findOnTransitionsFromStateByEventName(
        stateMachineClass.findStateClassByName("state1").get(),
        "e1"
      );
      assertEquals(1, t.size());
      assertEquals("state2", t.getFirst().getTargetStateName().get());

      assertEquals(
        0,
        stateMachineClass
          .findOnTransitionsFromStateByEventName(
            stateMachineClass.findStateClassByName("state1").get(),
            "nonExisting"
          )
          .size()
      );
    });
  }

  @Test
  void testToString() {
    assertEquals(stateMachineClass.toString(), "stateMachine1");
  }
}
