package at.ac.uibk.dps.cirrina.execution.`object`.expression

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import org.springframework.expression.*
import org.springframework.expression.spel.SpelCompilerMode
import org.springframework.expression.spel.SpelParserConfiguration
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

class SpelExpression(source: String) : Expression(source) {

  private val spelExpression: org.springframework.expression.Expression =
    try {
      PARSER.parseExpression(source)
    } catch (e: Exception) {
      error("the SpEL expression '$source' could not be parsed")
    }

  override fun execute(extent: Extent): Any? {
    return try {
      val context =
        StandardEvaluationContext().apply {
          addPropertyAccessor(ExtentPropertyAccessor(extent))
          setVariable("math", Math::class.java)
          setVariable("std", Stdlib::class.java)
          setRootObject(extent)
        }
      spelExpression.getValue(context)
    } catch (e: Exception) {
      val s = e.message
      println(e)
      error("Could not execute expression: ${e.message}")
    }
  }

  private class ExtentPropertyAccessor(private val extent: Extent) : PropertyAccessor {
    override fun getSpecificTargetClasses(): Array<Class<*>> = arrayOf(Extent::class.java)

    override fun canRead(context: EvaluationContext, target: Any?, name: String): Boolean =
      extent.has(name)

    override fun read(context: EvaluationContext, target: Any?, name: String): TypedValue =
      TypedValue(extent.resolve(name))

    override fun canWrite(context: EvaluationContext, target: Any?, name: String): Boolean =
      extent.has(name)

    override fun write(context: EvaluationContext, target: Any?, name: String, newValue: Any?) {
      extent.set(name, newValue)
    }
  }

  private companion object {
    private val CONFIG =
      SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, this::class.java.classLoader)
    private val PARSER = SpelExpressionParser(CONFIG)
  }
}
