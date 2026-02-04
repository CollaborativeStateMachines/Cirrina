package at.ac.uibk.dps.cirrina.execution.`object`.expression

class Stdlib {
  companion object {
    @JvmStatic fun randomPayload(sizes: IntArray) = ByteArray(sizes.random())

    @JvmStatic fun takeRandom(collection: Collection<*>): Any? = collection.randomOrNull()
  }
}
