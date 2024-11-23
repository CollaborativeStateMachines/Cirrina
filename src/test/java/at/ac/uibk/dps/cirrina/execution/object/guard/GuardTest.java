package at.ac.uibk.dps.cirrina.execution.object.guard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.GuardDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.context.InMemoryContext;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import org.junit.jupiter.api.Test;

class GuardTest {

  private final TracingAttributes attributes = new TracingAttributes("test","test",
      "test","test");

  @Test
  void testGuard() throws Exception {
    try (var context = new InMemoryContext(true)) {
      context.create("v", 5);

      var extent = new Extent(context);

      assertDoesNotThrow(() -> {
        var guardClass = new GuardDescription("v==5");

        var guard = GuardBuilder.from(guardClass).build();

        assertTrue(guard.evaluate(extent, attributes, null));

        guardClass = new GuardDescription("v==6");

        guard = GuardBuilder.from(guardClass).build();

        assertFalse(guard.evaluate(extent, attributes, null));
      });

      assertThrows(IllegalArgumentException.class, () -> {
        var guardClass = new GuardDescription("v");

        var guard = GuardBuilder.from(guardClass).build();

        guard.evaluate(extent, attributes, null);
      });
    }
  }
}
