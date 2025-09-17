package at.ac.uibk.dps.cirrina.execution.object.guard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cirrina.csm.Csml.GuardDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.context.InMemoryContext;
import org.junit.jupiter.api.Test;

class GuardTest {

  @Test
  void testGuard() throws Exception {
    try (var context = new InMemoryContext(true)) {
      context.create("v", 5);

      var extent = new Extent(context);

      assertDoesNotThrow(() -> {
        var guardClass = new GuardDescription("v==5");

        var guard = GuardBuilder.from(guardClass).build();

        assertTrue(guard.evaluate(extent));

        guardClass = new GuardDescription("v==6");

        guard = GuardBuilder.from(guardClass).build();

        assertFalse(guard.evaluate(extent));
      });

      assertThrows(IllegalArgumentException.class, () -> {
        var guardClass = new GuardDescription("v");

        var guard = GuardBuilder.from(guardClass).build();

        guard.evaluate(extent);
      });
    }
  }
}
