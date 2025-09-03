package at.ac.uibk.dps.cirrina.execution.object.expression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.context.InMemoryContext;
import java.nio.ByteBuffer;
import java.util.List;
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
  void testUtility() throws Exception {
    try (var context = new InMemoryContext(true)) {
      assertDoesNotThrow(() -> {
        var extent = new Extent(context);

        for (int i = 0; i < 100; ++i) {
          final var bytes = ExpressionBuilder.from(
            "utility:genRandPayload([1024, 1024 * 10, 1024 * 100, 1024 * 1000])"
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
      assertThrows(UnsupportedOperationException.class, () ->
        ExpressionBuilder.from("varOneInt = 2").build().execute(extent)
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
