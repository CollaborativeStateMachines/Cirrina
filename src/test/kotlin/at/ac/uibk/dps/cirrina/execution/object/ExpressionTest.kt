package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.spec.Expression
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExpressionTest {
  private fun withExpressionContext(block: ContextScope.() -> Unit) =
    ContextInMemory().use { context -> ContextScope(context, Extent.of(context)).block() }

  @Test
  fun testBasicExpressions() = withExpressionContext {
    val bytes = ByteArray(4).apply { ByteBuffer.wrap(this).putInt(0xBAD1D) }
    val variousList = listOf(-1, 1, true, "foobar")

    mapOf(
        "varPlusOneInt" to 1,
        "varNegativeOneInt" to -1,
        "varPlusOneDouble" to 1.0,
        "varNegativeOneDouble" to -1.0,
        "varTrueBool" to true,
        "varFalseBool" to false,
        "varFoobarString" to "foobar",
        "varBad1dBytes" to bytes,
        "varVariousList" to variousList,
      )
      .forEach { (k, v) -> context.create(k, v) }

    Expression("varPlusOneInt + 1") isEqualTo 2
    Expression("varNegativeOneInt - 1") isEqualTo -2
    Expression("varPlusOneDouble + 1.0") isEqualTo 2.0
    Expression("varNegativeOneDouble - 1.0") isEqualTo -2.0
    Expression("!varTrueBool") isEqualTo false
    Expression("!varFalseBool") isEqualTo true
    Expression("varFoobarString") isEqualTo "foobar"
    Expression("varBad1dBytes") isEqualTo bytes
    Expression("varVariousList") isEqualTo variousList
  }

  @Test
  fun testArrayArithmetic() = withExpressionContext {
    context.create("someArray", Expression("[1, 2, 3]").eval())

    listOf("someArray + [4]", "someArray + {5}", "someArray + [6, ...]").forEach {
      Expression("someArray = $it").eval()
    }

    val array = extent.resolve("someArray") as Array<*>
    Assertions.assertArrayEquals(arrayOf(1, 2, 3, 4, 5, 6), array)
    (1..6).forEach { Expression("someArray.contains($it)") isEqualTo true }

    // Removal tests
    Expression("someArray = someArray - [4]").eval()
    Expression("someArray = someArray - {5}").eval()
    Expression("someArray = someArray - [6, ...]").eval()

    Assertions.assertArrayEquals(arrayOf(1, 2, 3), extent.resolve("someArray") as Array<*>)
  }

  @Test
  fun testMapArithmetic() = withExpressionContext {
    context.create("someMap", Expression("{1:2}").eval())

    (3..11 step 2).forEach { k -> Expression("someMap = someMap + {$k:${k + 1}}").eval() }

    val expectedMap = mapOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10, 11 to 12)
    extent.resolve("someMap") isEqualTo expectedMap

    // Chained removals
    listOf(
        "someMap - {3:4}",
        "someMap - [5]",
        "someMap - [7, ...]",
        "someMap - {9}",
        "someMap - 11",
      )
      .forEach { Expression("someMap = $it").eval() }

    extent.resolve("someMap") isEqualTo mapOf(1 to 2)
  }

  @Test
  fun testUtility() = withExpressionContext {
    val expectedSizes = setOf(1024, 1024 * 10, 1024 * 100, 1024 * 1000)

    // Call utility function
    repeat(100) {
      val bytes =
        Expression("std:randomPayload([1024, 1024 * 10, 1024 * 100, 1024 * 1000])").eval()
          as ByteArray
      Assertions.assertTrue(bytes.size in expectedSizes)
    }

    repeat(100) {
      val v = Expression("std:takeRandom([1, 2, 3, ...])").eval()
      Assertions.assertTrue(v in listOf(1, 2, 3))
    }
  }

  @Test
  fun testMultiLineExpression() = withExpressionContext {
    context.create("varOneInt", 1)
    Expression(
      """
      let varExpressionLocal = 1; 
      varExpressionLocal += varOneInt; 
      varExpressionLocal
      """
        .trimIndent()
    ) isEqualTo 2
  }

  @Test
  fun testExpressionNegative() = withExpressionContext {
    context.create("varOneInt", 1)
    listOf("1 + ", "varInvalid", "!varInvalid", "varInvalid.sub", "varInvalid + 1")
      .map { Expression(it) }
      .forEach { assertThrows<IllegalStateException> { it.eval() } }
  }

  class ContextScope(val context: ContextInMemory, val extent: Extent) {
    fun Expression.eval(): Any? = evaluate(extent)

    infix fun Expression.isEqualTo(expected: Any?) {
      Assertions.assertEquals(expected, eval())
    }

    infix fun Any?.isEqualTo(expected: Any?) {
      Assertions.assertEquals(expected, this)
    }
  }
}
