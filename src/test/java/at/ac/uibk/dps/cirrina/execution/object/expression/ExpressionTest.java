package at.ac.uibk.dps.cirrina.execution.object.expression;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.context.InMemoryContext;
import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExpressionTest {

  @Test
  void testExpression() throws Exception {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        var bytes = new byte[4];
        ByteBuffer.wrap(bytes).putInt(0xBAD1D);
        var list = List.of(-1, 1, true, "foobar");

        context.create("varPlusOneInt", +1);
        context.create("varNegativeOneInt", -1);
        context.create("varPlusOneDouble", +1.0);
        context.create("varNegativeOneDouble", -1.0);
        context.create("varTrueBool", true);
        context.create("varFalseBool", false);
        context.create("varFoobarString", "foobar");
        context.create("varBad1dBytes", bytes);
        context.create("varVariousList", list);

        assertEquals(2, ExpressionBuilder.from("varPlusOneInt+1").build().execute(extent));
        assertEquals(-2, ExpressionBuilder.from("varNegativeOneInt-1").build().execute(extent));
        assertEquals(2.0, ExpressionBuilder.from("varPlusOneDouble+1.0").build().execute(extent));
        assertEquals(
          -2.0,
          ExpressionBuilder.from("varNegativeOneDouble-1.0").build().execute(extent)
        );
        assertEquals(false, ExpressionBuilder.from("!varTrueBool").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("!varFalseBool").build().execute(extent));
        assertEquals("foobar", ExpressionBuilder.from("varFoobarString").build().execute(extent));
        assertEquals(bytes, ExpressionBuilder.from("varBad1dBytes").build().execute(extent));
        assertEquals(list, ExpressionBuilder.from("varVariousList").build().execute(extent));
      });
    }
  }

  @Test
  void testArrayArithmetic() {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        // Array with 1, 2, 3
        context.create("someArray", ExpressionBuilder.from("[1, 2, 3]").build().execute(extent));

        final var ex = "someArray + [4] + {5} + [6, ...]";

        assertArrayEquals(
          new Object[] { 1, 2, 3, 4, 5, 6 },
          (Object[]) ExpressionBuilder.from(ex).build().execute(extent)
        );

        // Remove 4
        assertArrayEquals(
          new Object[] { 1, 2, 3, 5, 6 },
          (Object[]) ExpressionBuilder.from(ex + " - [4]").build().execute(extent)
        );

        // Remove 5
        assertArrayEquals(
          new Object[] { 1, 2, 3, 6 },
          (Object[]) ExpressionBuilder.from(ex + " - [4] - {5}").build().execute(extent)
        );

        // Remove 6
        assertArrayEquals(
          new Object[] { 1, 2, 3 },
          (Object[]) ExpressionBuilder.from(ex + " - [4] - {5} - [6, ...]").build().execute(extent)
        );
      });
    }
  }

  @Test
  void testListArithmetic() {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        // List with 1, 2, 3
        context.create(
          "someList",
          ExpressionBuilder.from("[1, 2, 3, ...]").build().execute(extent)
        );

        final var ex = "someList + [4] + {5} + [6, ...]";

        assertIterableEquals(
          List.of(1, 2, 3, 4, 5, 6),
          (List<?>) ExpressionBuilder.from(ex).build().execute(extent)
        );

        // Remove 4
        assertIterableEquals(
          List.of(1, 2, 3, 5, 6),
          (List<?>) ExpressionBuilder.from(ex + " - [4]").build().execute(extent)
        );

        // Remove 5
        assertIterableEquals(
          List.of(1, 2, 3, 6),
          (List<?>) ExpressionBuilder.from(ex + " - [4] - {5}").build().execute(extent)
        );

        // Remove 6
        assertIterableEquals(
          List.of(1, 2, 3),
          (List<?>) ExpressionBuilder.from(ex + " - [4] - {5} - [6, ...]").build().execute(extent)
        );
      });
    }
  }

  @Test
  void testSetArithmetic() {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        // Set with 1, 2, 3
        context.create("someSet", ExpressionBuilder.from("{1, 2, 3}").build().execute(extent));

        final var ex = "someSet + [4] + {5} + [6, ...]";

        assertIterableEquals(
          new LinkedHashSet<>(List.of(1, 2, 3, 4, 5, 6)),
          (Set<?>) ExpressionBuilder.from(ex).build().execute(extent)
        );

        // Remove 4
        assertIterableEquals(
          new LinkedHashSet<>(List.of(1, 2, 3, 5, 6)),
          (Set<?>) ExpressionBuilder.from(ex + " - [4]").build().execute(extent)
        );

        // Remove 5
        assertIterableEquals(
          new LinkedHashSet<>(List.of(1, 2, 3, 6)),
          (Set<?>) ExpressionBuilder.from(ex + " - [4] - {5}").build().execute(extent)
        );

        // Remove 6
        assertIterableEquals(
          new LinkedHashSet<>(List.of(1, 2, 3)),
          (Set<?>) ExpressionBuilder.from(ex + " - [4] - {5} - [6, ...]").build().execute(extent)
        );
      });
    }
  }

  @Test
  void testMapArithmetic() {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        // Map with 1:2
        context.create("someMap", ExpressionBuilder.from("{1:2}").build().execute(extent));

        final var ex = "(someMap + {3:4} + {5:6} + {7:8} + {9:10} + {11:12})";

        assertEquals(
          Map.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
          ExpressionBuilder.from(ex).build().execute(extent)
        );

        // Assert presence
        assertEquals(true, ExpressionBuilder.from(ex + ".contains(1)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from(ex + ".contains(3)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from(ex + ".contains(5)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from(ex + ".contains(7)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from(ex + ".contains(9)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from(ex + ".contains(11)").build().execute(extent));

        // Remove 3:4
        assertEquals(
          Map.of(1, 2, 5, 6, 7, 8, 9, 10, 11, 12),
          ExpressionBuilder.from(ex + " - {3:4}").build().execute(extent)
        );

        // Remove 5:6
        assertEquals(
          Map.of(1, 2, 7, 8, 9, 10, 11, 12),
          ExpressionBuilder.from(ex + " - {3:4} - [5]").build().execute(extent)
        );

        // Remove 7:8
        assertEquals(
          Map.of(1, 2, 9, 10, 11, 12),
          ExpressionBuilder.from(ex + " - {3:4} - [5] - [7, ...]").build().execute(extent)
        );

        // Remove 9:10
        assertEquals(
          Map.of(1, 2, 11, 12),
          ExpressionBuilder.from(ex + " - {3:4} - [5] - [7, ...] - {9}").build().execute(extent)
        );

        // Remove 11:12
        assertEquals(
          Map.of(1, 2),
          ExpressionBuilder.from(ex + " - {3:4} - {3:4} - [5] - [7, ...] - {9} - 11")
            .build()
            .execute(extent)
        );
      });
    }
  }

  @Test
  void testUtility() throws Exception {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        for (int i = 0; i < 100; ++i) {
          final var bytes = ExpressionBuilder.from(
            "std:genRandPayload([1024, 1024 * 10, 1024 * 100, 1024 * 1000])"
          )
            .build()
            .execute(extent);

          final var expectedOneOf = List.of(1024, 1024 * 10, 1024 * 100, 1024 * 1000);

          assertInstanceOf(byte[].class, bytes);
          assertTrue(expectedOneOf.contains(((byte[]) bytes).length));
        }
      });
    }
  }

  @Test
  void testExpressionUsingNamespace() throws Exception {
    try (var context = new InMemoryContext(true)) {
      assertEquals(1, ExpressionBuilder.from("math:abs(-1)").build().execute(new Extent(context)));
    }
  }

  @Test
  void testExpressionNegative() throws Exception {
    try (var context = new InMemoryContext(true)) {
      var extent = new Extent(context);

      context.create("varOneInt", 1);

      // Throws while parsing
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("1 + ").build().execute(extent)
      );
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("varOneInt = 2").build().execute(extent)
      );
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("let varOneInt = 2").build().execute(extent)
      );

      // Throws at runtime
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("varInvalid").build().execute(extent)
      );
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("!varInvalid").build().execute(extent)
      );
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("varInvalid.varInvalidSub").build().execute(extent)
      );
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("varInvalid + 1").build().execute(extent)
      );
    }
  }
}
