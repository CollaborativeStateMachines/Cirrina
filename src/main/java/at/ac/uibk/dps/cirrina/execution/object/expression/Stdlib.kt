package at.ac.uibk.dps.cirrina.execution.`object`.expression

import java.util.*

class Stdlib {
  companion object {
    @JvmStatic
    fun genRandPayload(sizes: IntArray): ByteArray {
      val rand = Random()

      val randomIndex = rand.nextInt(sizes.size)
      val selectedSize = sizes[randomIndex]

      return ByteArray(selectedSize)
    }
  }
}
