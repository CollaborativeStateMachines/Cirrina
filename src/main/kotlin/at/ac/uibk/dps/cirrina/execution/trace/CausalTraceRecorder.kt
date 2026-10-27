package at.ac.uibk.dps.cirrina.execution.trace

interface CausalTraceRecorder : AutoCloseable {
  val enabled: Boolean
    get() = true

  fun record(record: CausalEvent)

  companion object {
    val noop =
      object : CausalTraceRecorder {
        override val enabled = false

        override fun record(record: CausalEvent) {}

        override fun close() {}
      }
  }
}
