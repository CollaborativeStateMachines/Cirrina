package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.execution.`object`.Expression
import at.ac.uibk.dps.cirrina.execution.`object`.Extent
import java.lang.reflect.Array as ReflectArray
import java.util.LinkedHashSet
import org.apache.commons.jexl3.*
import org.apache.commons.jexl3.introspection.JexlPermissions

class ExpressionJexl(source: String) : Expression(source) {
  private val jexlScript: JexlScript =
    try {
      Provider.engine.createScript(source)
    } catch (e: Exception) {
      error("could not parse expression '$source': ${e.localizedMessage}")
    }

  override fun execute(extent: Extent): Any? =
    try {
      jexlScript.execute(ExtentJexlContext(extent))
    } catch (e: Exception) {
      error("failed to execute expression '$source': ${e.localizedMessage}")
    }

  private class ExtentJexlContext(private val extent: Extent) : JexlContext {
    override fun get(key: String): Any = extent.resolve(key)

    override fun set(key: String, value: Any?) {
      extent.set(key, value)
    }

    override fun has(key: String): Boolean = extent.has(key)
  }
}

private object Provider {
  private const val CACHE_SIZE = 1024

  val engine: JexlEngine =
    JexlBuilder()
      .arithmetic(CsmlArithmetic(true))
      .features(JexlFeatures().sideEffectGlobal(true).sideEffect(true))
      .cache(CACHE_SIZE)
      .namespaces(mapOf("math" to Math::class.java, "std" to Stdlib::class.java))
      .permissions(JexlPermissions.UNRESTRICTED)
      .strict(true)
      .silent(false)
      .antish(false)
      .safe(false)
      .create()
}

class Stdlib {
  companion object {
    @JvmStatic fun randomPayload(sizes: IntArray) = ByteArray(sizes.random())

    @JvmStatic fun takeRandom(collection: Collection<*>): Any? = collection.randomOrNull()
  }
}

private class CsmlArithmetic(strict: Boolean) : JexlArithmetic(strict) {
  override fun add(left: Any?, right: Any?): Any? =
    when (left) {
      is List<*> -> (left.asSequence() + right.toSequence()).toList()
      is Set<*> -> (left.asSequence() + right.toSequence()).toCollection(LinkedHashSet())
      is Map<*, *> -> (right as? Map<*, *>)?.let { left + it } ?: super.add(left, right)
      else ->
        left
          ?.takeIf { it.javaClass.isArray }
          ?.let { (it.toSequence() + right.toSequence()).toList().toTypedArray() }
          ?: super.add(left, right)
    }

  override fun subtract(left: Any?, right: Any?): Any? {
    if (left.isIterableLike() && right.isIterableLike()) {
      val toRemove = right.toSequence().toSet()
      val filtered = left.toSequence().filterNot { it in toRemove }

      return when (left) {
        is List<*> -> filtered.toList()
        is Set<*> -> filtered.toCollection(LinkedHashSet())
        else -> filtered.toList().toTypedArray()
      }
    }

    if (left is Map<*, *>) {
      val keysToRemove = right.toKeySequence().toSet()
      return left.filterKeys { it !in keysToRemove }
    }

    return super.subtract(left, right)
  }

  private fun Any?.isIterableLike(): Boolean =
    this is Collection<*> || (this != null && javaClass.isArray)

  private fun Any?.toSequence(): Sequence<Any?> =
    when (this) {
      null -> emptySequence()
      is Collection<*> -> asSequence()
      else ->
        if (javaClass.isArray) {
          (0 until ReflectArray.getLength(this)).asSequence().map { ReflectArray.get(this, it) }
        } else {
          sequenceOf(this)
        }
    }

  private fun Any?.toKeySequence(): Sequence<Any?> =
    when (this) {
      is Map<*, *> -> keys.asSequence()
      else -> toSequence()
    }
}
