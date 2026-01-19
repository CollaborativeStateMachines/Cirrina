package at.ac.uibk.dps.cirrina.execution.`object`.expression

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExpressionTest {

  @Test
  fun testExpression() {
    InMemoryContext(true).use { context ->
      val extent = Extent.of(context)
      val bytes = ByteArray(4).apply { ByteBuffer.wrap(this).putInt(0xBAD1D) }
      val list = listOf(-1, 1, true, "foobar")

      context.create("varPlusOneInt", +1)
      context.create("varNegativeOneInt", -1)
      context.create("varPlusOneDouble", +1.0)
      context.create("varNegativeOneDouble", -1.0)
      context.create("varTrueBool", true)
      context.create("varFalseBool", false)
      context.create("varFoobarString", "foobar")
      context.create("varBad1dBytes", bytes)
      context.create("varVariousList", list)

      assertDoesNotThrow {
        assertEquals(2, "varPlusOneInt+1".eval(extent))
        assertEquals(-2, "varNegativeOneInt-1".eval(extent))
        assertEquals(2.0, "varPlusOneDouble+1.0".eval(extent))
        assertEquals(-2.0, "varNegativeOneDouble-1.0".eval(extent))
        assertEquals(false, "!varTrueBool".eval(extent))
        assertEquals(true, "!varFalseBool".eval(extent))
        assertEquals("foobar", "varFoobarString".eval(extent))
        assertEquals(bytes, "varBad1dBytes".eval(extent))
        assertEquals(list, "varVariousList".eval(extent))
      }
    }
  }

  @Test
  fun testArrayArithmetic() {
    InMemoryContext(true).use { context ->
      val extent = Extent.of(context)
      assertDoesNotThrow {
        // Array with 1, 2, 3
        context.create("someArray", "[1, 2, 3]".eval(extent))

        // Arithmetic additions
        "someArray = someArray + [4]".eval(extent)
        "someArray = someArray + {5}".eval(extent)
        "someArray = someArray + [6, ...]".eval(extent)

        assertArrayEquals(arrayOf(1, 2, 3, 4, 5, 6), extent.resolve("someArray") as Array<*>)

        // Verifications
        (1..6).forEach { assertEquals(true, "someArray.contains($it)".eval(extent)) }

        // Arithmetic subtractions
        "someArray = someArray - [4]".eval(extent)
        assertArrayEquals(arrayOf(1, 2, 3, 5, 6), extent.resolve("someArray") as Array<*>)

        "someArray = someArray - {5}".eval(extent)
        assertArrayEquals(arrayOf(1, 2, 3, 6), extent.resolve("someArray") as Array<*>)

        "someArray = someArray - [6, ...]".eval(extent)
        assertArrayEquals(arrayOf(1, 2, 3), extent.resolve("someArray") as Array<*>)
      }
    }
  }

  @Test
  fun testListArithmetic() {
    InMemoryContext(true).use { context ->
      val extent = Extent.of(context)
      assertDoesNotThrow {
        context.create("someList", "[1, 2, 3, ...]".eval(extent))

        "someList = someList + [4]".eval(extent)
        "someList = someList + {5}".eval(extent)
        "someList = someList + [6, ...]".eval(extent)

        assertIterableEquals(listOf(1, 2, 3, 4, 5, 6), extent.resolve("someList") as List<*>)

        "someList = someList - [4]".eval(extent)
        "someList = someList - {5}".eval(extent)
        "someList = someList - [6, ...]".eval(extent)

        assertIterableEquals(listOf(1, 2, 3), extent.resolve("someList") as List<*>)
      }
    }
  }

  @Test
  fun testSetArithmetic() {
    InMemoryContext(true).use { context ->
      val extent = Extent.of(context)
      assertDoesNotThrow {
        context.create("someSet", "{1, 2, 3}".eval(extent))

        "someSet = someSet + [4]".eval(extent)
        "someSet = someSet + {5}".eval(extent)
        "someSet = someSet + [6, ...]".eval(extent)

        val expected = linkedSetOf(1, 2, 3, 4, 5, 6)
        assertIterableEquals(expected, extent.resolve("someSet") as Set<*>)
      }
    }
  }

  @Test
  fun testMapArithmetic() {
    InMemoryContext(true).use { context ->
      val extent = Extent.of(context)
      assertDoesNotThrow {
        context.create("someMap", "{1:2}".eval(extent))

        listOf("3:4", "5:6", "7:8", "9:10", "11:12").forEach {
          "someMap = someMap + {$it}".eval(extent)
        }

        val expectedMap = mapOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10, 11 to 12)
        assertEquals(expectedMap, extent.resolve("someMap"))

        "someMap = someMap - {3:4}".eval(extent)
        "someMap = someMap - [5]".eval(extent)
        "someMap = someMap - [7, ...]".eval(extent)
        "someMap = someMap - {9}".eval(extent)
        "someMap = someMap - 11".eval(extent)

        assertEquals(mapOf(1 to 2), extent.resolve("someMap"))
      }
    }
  }

  @Test
  fun testUtility() {
    InMemoryContext(true).use { context ->
      val extent = Extent.of(context)
      val expectedOneOf = listOf(1024, 1024 * 10, 1024 * 100, 1024 * 1000)

      repeat(100) {
        val bytes = "std:genRandPayload([1024, 1024 * 10, 1024 * 100, 1024 * 1000])".eval(extent)
        assertInstanceOf(ByteArray::class.java, bytes)
        assertTrue(expectedOneOf.contains((bytes as ByteArray).size))
      }
    }
  }

  @Test
  fun testMultiLineExpression() {
    InMemoryContext(true).use { context ->
      val extent = Extent.of(context)
      context.create("varOneInt", 1)
      val expr = "let varExpressionLocal = 1; varExpressionLocal += varOneInt; varExpressionLocal"
      assertEquals(2, expr.eval(extent))
    }
  }

  @Test
  fun testExpressionNegative() {
    InMemoryContext(true).use { context ->
      val extent = Extent.of(context)
      context.create("varOneInt", 1)

      assertThrows<UnsupportedOperationException> { "1 + ".eval(extent) }
      assertThrows<UnsupportedOperationException> { "varInvalid".eval(extent) }
      assertThrows<UnsupportedOperationException> { "!varInvalid".eval(extent) }
      assertThrows<UnsupportedOperationException> { "varInvalid.sub".eval(extent) }
      assertThrows<UnsupportedOperationException> { "varInvalid + 1".eval(extent) }
    }
  }

  private fun String.eval(extent: Extent): Any? =
    ExpressionBuilder.from(this).build().getOrThrow().execute(extent).getOrThrow()
}
