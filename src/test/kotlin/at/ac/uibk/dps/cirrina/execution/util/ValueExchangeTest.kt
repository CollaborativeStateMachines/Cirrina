package at.ac.uibk.dps.cirrina.execution.util

import kotlin.collections.get
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test

class ValueExchangeTest {

  @Test
  fun testToFromBytes() {
    val primitives = listOf(1, 1.0f, 1L, 1.0, "1", true)
    val byteArray =
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
    val complexArray: Array<Any?> = arrayOf(1, 1.0f, 1L, 1.0, "1", true, byteArray)
    val complexMap = mapOf(1 to 1.0f, 1L to 1.0, "1" to true)

    // Primitives
    primitives.forEach { assertEquals(it, it.roundTrip()) }

    // Byte array
    assertArrayEquals(byteArray, byteArray.roundTrip() as ByteArray)

    // Complex array
    assertArrayEquals(complexArray, complexArray.roundTrip() as Array<*>)

    // Primitives list
    assertIterableEquals(primitives, primitives.roundTrip() as List<*>)

    // Complex map
    (complexMap.roundTrip() as Map<*, *>).let { returnedMap ->
      assertEquals(complexMap.size, returnedMap.size)
      complexMap.forEach { (k, v) -> assertEquals(v, returnedMap[k]) }
    }
  }

  private fun Any?.roundTrip(): Any? = ValueExchange.fromBytes(ValueExchange(this).toBytes()).value
}
