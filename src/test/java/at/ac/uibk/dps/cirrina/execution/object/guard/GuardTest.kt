package at.ac.uibk.dps.cirrina.execution.object.guard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var guard = GuardBuilder.from("v==5").build();

        assertTrue(guard.evaluate(extent));

        guard = GuardBuilder.from("v==6").build();

        assertFalse(guard.evaluate(extent));
      });

      assertThrows(IllegalArgumentException.class, () -> {
        var guard = GuardBuilder.from("v").build();

        guard.evaluate(extent);
      });
    }
  }
}
