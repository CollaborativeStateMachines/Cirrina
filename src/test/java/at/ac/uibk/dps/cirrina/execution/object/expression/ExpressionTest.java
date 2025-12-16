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

        // Add 4, 5, 6
        ExpressionBuilder.from("someArray = someArray + [4]").build().execute(extent);
        ExpressionBuilder.from("someArray = someArray + {5}").build().execute(extent);
        ExpressionBuilder.from("someArray = someArray + [6, ...]").build().execute(extent);

        assertArrayEquals(
          new Object[] { 1, 2, 3, 4, 5, 6 },
          (Object[]) extent.resolve("someArray").get()
        );

        // Assert presence
        assertEquals(true, ExpressionBuilder.from("someArray.contains(1)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someArray.contains(2)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someArray.contains(3)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someArray.contains(4)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someArray.contains(5)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someArray.contains(6)").build().execute(extent));

        // Remove 4
        ExpressionBuilder.from("someArray = someArray - [4]").build().execute(extent);

        assertArrayEquals(
          new Object[] { 1, 2, 3, 5, 6 },
          (Object[]) extent.resolve("someArray").get()
        );

        // Remove 5
        ExpressionBuilder.from("someArray = someArray - {5}").build().execute(extent);

        assertArrayEquals(
          new Object[] { 1, 2, 3, 6 },
          (Object[]) extent.resolve("someArray").get()
        );

        // Remove 6
        ExpressionBuilder.from("someArray = someArray - [6, ...]").build().execute(extent);

        assertArrayEquals(new Object[] { 1, 2, 3 }, (Object[]) extent.resolve("someArray").get());

        // Assert absence
        assertEquals(
          false,
          ExpressionBuilder.from("someArray.contains(4)").build().execute(extent)
        );
        assertEquals(
          false,
          ExpressionBuilder.from("someArray.contains(5)").build().execute(extent)
        );
        assertEquals(
          false,
          ExpressionBuilder.from("someArray.contains(6)").build().execute(extent)
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

        // Add 4, 5, 6
        ExpressionBuilder.from("someList = someList + [4]").build().execute(extent);
        ExpressionBuilder.from("someList = someList + {5}").build().execute(extent);
        ExpressionBuilder.from("someList = someList + [6, ...]").build().execute(extent);

        assertIterableEquals(List.of(1, 2, 3, 4, 5, 6), (List<?>) extent.resolve("someList").get());

        // Assert presence
        assertEquals(true, ExpressionBuilder.from("someList.contains(1)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(2)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(3)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(4)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(5)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(6)").build().execute(extent));

        // Remove 4
        ExpressionBuilder.from("someList = someList - [4]").build().execute(extent);

        assertIterableEquals(List.of(1, 2, 3, 5, 6), (List<?>) extent.resolve("someList").get());

        // Remove 5
        ExpressionBuilder.from("someList = someList - {5}").build().execute(extent);

        assertIterableEquals(List.of(1, 2, 3, 6), (List<?>) extent.resolve("someList").get());

        // Remove 6
        ExpressionBuilder.from("someList = someList - [6, ...]").build().execute(extent);

        assertIterableEquals(List.of(1, 2, 3), (List<?>) extent.resolve("someList").get());

        // Assert absence
        assertEquals(false, ExpressionBuilder.from("someList.contains(4)").build().execute(extent));
        assertEquals(false, ExpressionBuilder.from("someList.contains(5)").build().execute(extent));
        assertEquals(false, ExpressionBuilder.from("someList.contains(6)").build().execute(extent));
      });
    }
  }

  @Test
  void testSetArithmetic() {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        // Set with 1, 2, 3
        context.create("someList", ExpressionBuilder.from("{1, 2, 3}").build().execute(extent));

        // Add 4, 5, 6
        ExpressionBuilder.from("someList = someList + [4]").build().execute(extent);
        ExpressionBuilder.from("someList = someList + {5}").build().execute(extent);
        ExpressionBuilder.from("someList = someList + [6, ...]").build().execute(extent);

        assertIterableEquals(
          new LinkedHashSet<>(List.of(1, 2, 3, 4, 5, 6)),
          (Set<?>) extent.resolve("someList").get()
        );

        // Assert presence
        assertEquals(true, ExpressionBuilder.from("someList.contains(1)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(2)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(3)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(4)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(5)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someList.contains(6)").build().execute(extent));

        // Remove 4
        ExpressionBuilder.from("someList = someList - [4]").build().execute(extent);

        assertIterableEquals(
          new LinkedHashSet<>(List.of(1, 2, 3, 5, 6)),
          (Set<?>) extent.resolve("someList").get()
        );

        // Remove 5
        ExpressionBuilder.from("someList = someList - {5}").build().execute(extent);

        assertIterableEquals(
          new LinkedHashSet<>(List.of(1, 2, 3, 6)),
          (Set<?>) extent.resolve("someList").get()
        );

        // Remove 6
        ExpressionBuilder.from("someList = someList - [6, ...]").build().execute(extent);

        assertIterableEquals(
          new LinkedHashSet<>(List.of(1, 2, 3)),
          (Set<?>) extent.resolve("someList").get()
        );

        // Assert absence
        assertEquals(false, ExpressionBuilder.from("someList.contains(4)").build().execute(extent));
        assertEquals(false, ExpressionBuilder.from("someList.contains(5)").build().execute(extent));
        assertEquals(false, ExpressionBuilder.from("someList.contains(6)").build().execute(extent));
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

        // Add 3:4, 5:6, 7:8, 9:10, 11:12
        ExpressionBuilder.from("someMap = someMap + {3:4}").build().execute(extent);
        ExpressionBuilder.from("someMap = someMap + {5:6}").build().execute(extent);
        ExpressionBuilder.from("someMap = someMap + {7:8}").build().execute(extent);
        ExpressionBuilder.from("someMap = someMap + {9:10}").build().execute(extent);
        ExpressionBuilder.from("someMap = someMap + {11:12}").build().execute(extent);

        assertEquals(
          Map.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
          extent.resolve("someMap").get()
        );

        // Assert presence
        assertEquals(true, ExpressionBuilder.from("someMap.contains(1)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someMap.contains(3)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someMap.contains(5)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someMap.contains(7)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someMap.contains(9)").build().execute(extent));
        assertEquals(true, ExpressionBuilder.from("someMap.contains(11)").build().execute(extent));

        // Remove 3:4
        ExpressionBuilder.from("someMap = someMap - {3:4}").build().execute(extent);

        assertEquals(Map.of(1, 2, 5, 6, 7, 8, 9, 10, 11, 12), extent.resolve("someMap").get());

        // Remove 5:6
        ExpressionBuilder.from("someMap = someMap - [5]").build().execute(extent);

        assertEquals(Map.of(1, 2, 7, 8, 9, 10, 11, 12), extent.resolve("someMap").get());

        // Remove 7:8
        ExpressionBuilder.from("someMap = someMap - [7, ...]").build().execute(extent);

        assertEquals(Map.of(1, 2, 9, 10, 11, 12), extent.resolve("someMap").get());

        // Remove 9:10
        ExpressionBuilder.from("someMap = someMap - {9}").build().execute(extent);

        assertEquals(Map.of(1, 2, 11, 12), extent.resolve("someMap").get());

        // Remove 11:12
        ExpressionBuilder.from("someMap = someMap - 11").build().execute(extent);

        assertEquals(Map.of(1, 2), extent.resolve("someMap").get());

        // Assert absence
        assertEquals(false, ExpressionBuilder.from("someMap.contains(3)").build().execute(extent));
        assertEquals(false, ExpressionBuilder.from("someMap.contains(5)").build().execute(extent));
        assertEquals(false, ExpressionBuilder.from("someMap.contains(7)").build().execute(extent));
        assertEquals(false, ExpressionBuilder.from("someMap.contains(9)").build().execute(extent));
        assertEquals(false, ExpressionBuilder.from("someMap.contains(11)").build().execute(extent));
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
  void testMultiLineExpression() throws Exception {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        context.create("varOneInt", 1);

        var multiLineExpression =
          "let varExpressionLocal = 1; varExpressionLocal += varOneInt; varExpressionLocal";
        assertEquals(2, ExpressionBuilder.from(multiLineExpression).build().execute(extent));
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
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("let varTemp = varInvalid; varTemp").build().execute(extent)
      );
    }
  }
}
