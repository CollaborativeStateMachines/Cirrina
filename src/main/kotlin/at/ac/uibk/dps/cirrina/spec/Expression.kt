package at.ac.uibk.dps.cirrina.spec

class Expression(val source: String) {
  companion object {
    fun create(source: String) = runCatching { Expression(source) }
  }
}
