package at.ac.uibk.dps.cirrina.execution.`object`.expression

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import java.lang.reflect.Array as ReflectArray
import java.util.LinkedHashSet
import org.apache.commons.jexl3.*
import org.apache.commons.jexl3.introspection.JexlPermissions

class JexlExpression(source: String) : Expression(source) {

  private val jexlScript: JexlScript =
    try {
      JEXL_ENGINE.createScript(source)
    } catch (e: Exception) {
      throw UnsupportedOperationException("the JEXL expression '$source' could not be parsed", e)
    }

  override fun execute(extent: Extent): Result<Any?> =
    runCatching { jexlScript.execute(ExtentJexlContext(extent)) }
      .recoverCatching { e ->
        throw UnsupportedOperationException(
          "the JEXL expression '${jexlScript.sourceText}' could not be executed",
          e,
        )
      }

  private inner class ExtentJexlContext(private val extent: Extent) : JexlContext {
    override fun get(key: String): Any? = extent.resolve(key)

    override fun set(key: String, value: Any?) {
      extent.trySet(key, value)
    }

    override fun has(key: String): Boolean = extent.resolve(key) != null
  }

  private companion object {
    private const val CACHE_SIZE = 512

    private val JEXL_ENGINE: JexlEngine =
      JexlBuilder()
        .arithmetic(CsmlArithmetic(true))
        .features(JexlFeatures().sideEffectGlobal(true).sideEffect(true))
        .cache(CACHE_SIZE)
        .namespaces(mapOf("math" to Math::class.java, "std" to Stdlib::class.java))
        .permissions(JexlPermissions.UNRESTRICTED)
        .strict(true)
        .silent(false)
        .create()
  }

  private class CsmlArithmetic(strict: Boolean) : JexlArithmetic(strict) {

    override fun add(left: Any?, right: Any?): Any? =
      when (left) {
        is List<*> -> left + right.toIterable()
        is Set<*> -> (left + right.toIterable()).toCollection(LinkedHashSet())
        is Map<*, *> -> if (right is Map<*, *>) left + right else super.add(left, right)
        else ->
          when {
            left?.javaClass?.isArray == true -> {
              val combined = left.toIterable() + right.toIterable()
              combined.toArray(left.javaClass.componentType)
            }
            else -> super.add(left, right)
          }
      }

    override fun subtract(left: Any?, right: Any?): Any? {
      val rightSet = right.toIterable().toSet()
      return when (left) {
        is List<*> -> left.filterNot { it in rightSet }
        is Set<*> -> left.filterNotTo(LinkedHashSet()) { it in rightSet }
        is Map<*, *> -> {
          val keysToRemove =
            when (right) {
              is Map<*, *> -> right.keys
              is Iterable<*> -> right.toSet()
              else -> setOf(right)
            }
          left.filterKeys { it !in keysToRemove }
        }
        else ->
          when {
            left?.javaClass?.isArray == true -> {
              left.toIterable().filterNot { it in rightSet }.toArray(left.javaClass.componentType)
            }
            else -> super.subtract(left, right)
          }
      }
    }

    private fun Any?.toIterable(): Iterable<*> =
      when (this) {
        is Iterable<*> -> this
        is Array<*> -> this.asIterable()
        null -> emptyList<Any>()
        else ->
          if (javaClass.isArray) {
            (0 until ReflectArray.getLength(this)).map { ReflectArray.get(this, it) }
          } else {
            listOf(this)
          }
      }

    private fun List<*>.toArray(componentType: Class<*>): Any {
      val array = ReflectArray.newInstance(componentType, size)
      forEachIndexed { i, e -> ReflectArray.set(array, i, e) }
      return array
    }
  }
}
