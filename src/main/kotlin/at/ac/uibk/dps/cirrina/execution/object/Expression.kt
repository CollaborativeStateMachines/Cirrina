package at.ac.uibk.dps.cirrina.execution.`object`

import java.lang.reflect.Array as ReflectArray
import java.security.SecureRandom
import java.util.LinkedHashSet
import kotlin.random.Random
import org.apache.commons.jexl3.JexlArithmetic
import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.JexlEngine
import org.apache.commons.jexl3.JexlFeatures
import org.apache.commons.jexl3.introspection.JexlPermissions

fun String.evaluatesToTrue(extent: Extent): Boolean {
  val result = this.evaluate(extent)
  require(result is Boolean) { "guard expression '$this' did not produce a boolean" }
  return result
}

fun String.evaluate(extent: Extent): Any? {
  val script =
    try {
      Provider.engine.createScript(this)
    } catch (e: Exception) {
      error("could not parse expression '$this': ${e.localizedMessage}")
    }

  return try {
    script.execute(extent)
  } catch (e: Exception) {
    error("failed to execute expression '$this': ${e.localizedMessage}")
  }
}

fun String.evaluate() = this.evaluate(Extent.empty())

private object Provider {
  private const val CACHE_SIZE = 1024

  val engine: JexlEngine =
    JexlBuilder()
      .arithmetic(CsmlArithmetic(true))
      .features(JexlFeatures().sideEffectGlobal(true).sideEffect(true))
      .cache(CACHE_SIZE)
      .namespaces(mapOf("math" to Math::class.java, "std" to Stdlib::class.java))
      .permissions(JexlPermissions.UNRESTRICTED)
      .strict(false)
      .silent(false)
      .antish(false)
      .safe(false)
      .create()
}

class Stdlib {
  companion object {
    private val seedGenerator = SecureRandom()

    private val threadRng =
      object : ThreadLocal<Random>() {
        override fun initialValue(): Random {
          return Random(seedGenerator.nextLong())
        }
      }

    @JvmStatic
    fun seed(seed: Long) {
      threadRng.set(Random(seed))
    }

    @JvmStatic
    fun randomPayload(sizes: IntArray): ByteArray {
      val rng = threadRng.get()
      return ByteArray(sizes.random(rng))
    }

    @JvmStatic
    fun takeRandom(collection: Collection<*>): Any? {
      return collection.randomOrNull(threadRng.get())
    }

    @JvmStatic fun takeRandom(array: Array<Any>): Any? = array.randomOrNull(threadRng.get())

    @JvmStatic
    fun randomAround(base: Int, delta: Int): Int {
      val rng = threadRng.get()
      return (base - delta..base + delta).random(rng)
    }

    @JvmStatic fun repeat(item: Boolean, n: Int) = BooleanArray(n) { item }

    @JvmStatic fun repeat(item: Byte, n: Int) = ByteArray(n) { item }

    @JvmStatic fun repeat(item: Char, n: Int) = CharArray(n) { item }

    @JvmStatic fun repeat(item: Short, n: Int) = ShortArray(n) { item }

    @JvmStatic fun repeat(item: Int, n: Int) = IntArray(n) { item }

    @JvmStatic fun repeat(item: Long, n: Int) = LongArray(n) { item }

    @JvmStatic fun repeat(item: Float, n: Int) = FloatArray(n) { item }

    @JvmStatic fun repeat(item: Double, n: Int) = DoubleArray(n) { item }
  }
}

private class CsmlArithmetic(strict: Boolean) : JexlArithmetic(strict) {

  override fun add(left: Any?, right: Any?): Any? {
    return when (left) {
      is List<*> -> {
        val result = ArrayList<Any?>((left.size) + (estimateSize(right)))
        result.addAll(left)
        appendAll(result, right)
        result
      }
      is Set<*> -> {
        val result = LinkedHashSet<Any?>((left.size) + (estimateSize(right)))
        result.addAll(left)
        appendAll(result, right)
        result
      }
      is Map<*, *> -> (right as? Map<*, *>)?.let { left + it } ?: super.add(left, right)
      else -> {
        if (left != null && left.javaClass.isArray) {
          val result = ArrayList<Any?>(estimateSize(left) + estimateSize(right))
          appendAll(result, left)
          appendAll(result, right)
          result.toTypedArray()
        } else {
          super.add(left, right)
        }
      }
    }
  }

  override fun subtract(left: Any?, right: Any?): Any? {
    if (isIterableLike(left) && isIterableLike(right)) {
      val toRemove = buildRemovalSet(right)

      return when (left) {
        is List<*> -> left.filterNot { it in toRemove }
        is Set<*> -> left.filterNotTo(LinkedHashSet()) { it in toRemove }
        else -> {
          val result = ArrayList<Any?>()
          val length = ReflectArray.getLength(left!!)
          for (i in 0 until length) {
            val item = ReflectArray.get(left, i)
            if (item !in toRemove) result.add(item)
          }
          result.toTypedArray()
        }
      }
    }

    if (left is Map<*, *>) {
      val keysToRemove = buildRemovalSet(right)
      return left.filterKeys { it !in keysToRemove }
    }

    return super.subtract(left, right)
  }

  private fun isIterableLike(obj: Any?): Boolean =
    obj is Collection<*> || (obj != null && obj.javaClass.isArray)

  private fun estimateSize(obj: Any?): Int =
    when (obj) {
      is Collection<*> -> obj.size
      is Map<*, *> -> obj.size
      null -> 0
      else -> if (obj.javaClass.isArray) ReflectArray.getLength(obj) else 1
    }

  private fun appendAll(target: MutableCollection<Any?>, source: Any?) {
    when (source) {
      is Collection<*> -> target.addAll(source)
      null -> return
      else -> {
        if (source.javaClass.isArray) {
          val length = ReflectArray.getLength(source)
          for (i in 0 until length) {
            target.add(ReflectArray.get(source, i))
          }
        } else {
          target.add(source)
        }
      }
    }
  }

  private fun buildRemovalSet(obj: Any?): Set<Any?> {
    val set = HashSet<Any?>(estimateSize(obj))
    when (obj) {
      is Map<*, *> -> set.addAll(obj.keys)
      is Collection<*> -> set.addAll(obj)
      null -> return set
      else -> {
        if (obj.javaClass.isArray) {
          val length = ReflectArray.getLength(obj)
          for (i in 0 until length) {
            set.add(ReflectArray.get(obj, i))
          }
        } else {
          set.add(obj)
        }
      }
    }
    return set
  }
}
