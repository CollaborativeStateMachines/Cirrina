package at.ac.uibk.dps.cirrina.spec

open class ContextVariable(val name: String, open val value: Any?) {
  init {
    require(name.isNotBlank()) { "name cannot be blank" }
  }

  override fun toString(): String = "ContextVariable(name='$name', value=$value)"
}

class LazyContextVariable(name: String, val expression: String) :
  ContextVariable(name, expression) {

  override fun toString(): String = "LazyContextVariable(name='$name', expression=$expression)"
}
