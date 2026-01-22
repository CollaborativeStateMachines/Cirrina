package at.ac.uibk.dps.cirrina.execution.`object`.exchange

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test

class ValueExchangeTest {

  @Test
  fun testToFromBytes() {
    val i = 1
    val f = 1.0f
    val l = 1L
    val d = 1.0
    val s = "1"
    val bo = true
    val by =
      byteArrayOf(
        8,
        1,
        16,
        0,
        0,
        0,
        63,
        24,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        33,
        8,
        49,
        16,
        1,
        26,
        1,
        49,
        8,
        32,
      )
    val ar = arrayOf(i, f, l, d, s, bo, by)
    val li = listOf(i, f, l, d, s, bo)
    val ma = mapOf(i to f, l to d, s to bo)

    assertDoesNotThrow {
      fun roundTrip(value: Any?): Any? =
        ValueExchange.fromBytes(ValueExchange(value).toBytes()).value

      assertEquals(i, roundTrip(i))
      assertEquals(f, roundTrip(f))
      assertEquals(l, roundTrip(l))
      assertEquals(d, roundTrip(d))
      assertEquals(s, roundTrip(s))
      assertEquals(bo, roundTrip(bo))

      assertArrayEquals(by, roundTrip(by) as ByteArray)
      assertArrayEquals(ar, roundTrip(ar) as Array<*>)
      assertIterableEquals(li, roundTrip(li) as List<*>)

      val returnedMap = roundTrip(ma) as Map<*, *>
      assertIterableEquals(ma.entries, returnedMap.entries)
    }
  }
}
