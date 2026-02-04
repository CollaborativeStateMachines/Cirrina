package at.ac.uibk.dps.cirrina.execution.`object`

class Guard private constructor(val expression: Expression) {
  fun evaluate(extent: Extent): Boolean {
    val result = expression.execute(extent)
    require(result is Boolean) { "guard expression '$expression' did not produce a boolean" }
    return result
  }

  override fun toString(): String = "Guard(expression='$expression')"

  companion object {
    fun from(expressionDescription: String) = Guard(Expression.from(expressionDescription))
  }
}
