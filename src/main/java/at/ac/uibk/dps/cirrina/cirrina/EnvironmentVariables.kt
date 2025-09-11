package at.ac.uibk.dps.cirrina.cirrina

/** Provider for events. */
enum class EventProvider {
  NATS
}

/** Provider for the persistent context. */
enum class PersistentContextProvider {
  NATS
}

/** An environment variable, which can be converted from a string to the required type. */
data class EnvironmentVariable<T>(
  val name: String, val required: Boolean = false, val default: T? = null, val mapper: (String) -> T = {
    it as T
  }
) {
  /** Get the value of the environment variable. */
  fun get(): T {
    val value = System.getenv(name)
    return when {
      value != null -> mapper(value)
      default != null -> default
      required -> throw EnvironmentVariableError.Missing("Missing required environment variable: $name")
      else -> throw EnvironmentVariableError.Missing("Environment variable '$name' is missing but not marked required")
    }
  }
}

/** Common interface for acquiring environment variables. */
object EnvironmentVariables {
  /** The NATS event server URL. */
  val natsEventUrl = EnvironmentVariable("NATS_EVENT_URL", default = "nats://localhost:4222/")

  /** The NATS persistent context server URL. */
  val natsPersistentContextUrl = EnvironmentVariable("NATS_PERSISTENT_CONTEXT_URL", default = "nats://localhost:4222")

  /** The NATS persistent context bucket. */
  val natsPersistentContextBucket = EnvironmentVariable("NATS_PERSISTENT_CONTEXT_BUCKET", default = "persistent")

  /** The path to the CSML application. */
  val applicationPath = EnvironmentVariable<String>("APPLICATION_PATH", required = true)

  /** The state machine names to instantiate. */
  val instantiate =
    EnvironmentVariable(
      name = "INSTANTIATE",
      default = emptyList(),
      mapper = { value -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() } },
    )

  /** The event provider to use. */
  val eventProvider =
    EnvironmentVariable(
      name = "EVENT_PROVIDER",
      default = EventProvider.NATS,
      mapper = { value ->
        try {
          EventProvider.valueOf(value.uppercase())
        } catch (_: IllegalArgumentException) {
          throw EnvironmentVariableError.Invalid(
            "EVENT_PROVIDER", value, EventProvider.entries.toString()
          )
        }
      },
    )

  /** The persistent context provider to use. */
  val persistentContextProvider =
    EnvironmentVariable(
      name = "PERSISTENT_CONTEXT_PROVIDER",
      default = PersistentContextProvider.NATS,
      mapper = { value ->
        try {
          PersistentContextProvider.valueOf(value.uppercase())
        } catch (_: IllegalArgumentException) {
          throw EnvironmentVariableError.Invalid(
            "PERSISTENT_CONTEXT_PROVIDER", value, PersistentContextProvider.entries.toString()
          )
        }
      },
    )

  /** The port to use for the health server. */
  val healthPort = EnvironmentVariable("HEALTH_PORT", default = 0xCAFE) { it.toInt() }
}


