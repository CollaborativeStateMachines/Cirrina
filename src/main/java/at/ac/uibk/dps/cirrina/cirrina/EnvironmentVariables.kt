package at.ac.uibk.dps.cirrina.cirrina

/** Provider for events. */
enum class EventProvider {
  NATS
}

/** Provider for the persistent context. */
enum class PersistentContextProvider {
  ETCD
}

/** An environment variable, which can be converted from a string to the required type. */
data class EnvironmentVariable<T>(
  val name: String,
  val required: Boolean = false,
  val default: T? = null,
  val mapper: (String) -> T = { it as T },
) {
  /** Get the value of the environment variable. */
  fun get(): T {
    val value = System.getenv(name)
    return when {
      value != null -> mapper(value)
      default != null -> default
      required ->
        throw EnvironmentVariableError.Missing("missing required environment variable: $name")
      else ->
        throw EnvironmentVariableError.Missing(
          "environment variable '$name' is missing but not marked required"
        )
    }
  }
}

/** Common interface for acquiring environment variables. */
object EnvironmentVariables {
  /** The NATS event server URL. */
  val natsEventUrl = EnvironmentVariable("NATS_EVENT_URL", default = "nats://localhost:4222/")

  /** The Etcd context server URL. */
  val etcdContextUrl = EnvironmentVariable("ETCD_CONTEXT_URL", default = "http://localhost:2379")

  /** The path to the CSML application. */
  val appPath = EnvironmentVariable<String>("APP_PATH", required = true)

  /** The path to the service implementation bindings. */
  val serviceBindingsPath = EnvironmentVariable<String>("SERVICE_BINDINGS_PATH", required = true)

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
            "EVENT_PROVIDER",
            value,
            EventProvider.entries.toString(),
          )
        }
      },
    )

  /** The context provider to use. */
  val contextProvider =
    EnvironmentVariable(
      name = "CONTEXT_PROVIDER",
      default = PersistentContextProvider.ETCD,
      mapper = { value ->
        try {
          PersistentContextProvider.valueOf(value.uppercase())
        } catch (_: IllegalArgumentException) {
          throw EnvironmentVariableError.Invalid(
            "CONTEXT_PROVIDER",
            value,
            PersistentContextProvider.entries.toString(),
          )
        }
      },
    )

  /** The port to use for the health server. */
  val healthPort = EnvironmentVariable("HEALTH_PORT", default = 0xCAFE) { it.toInt() }
}
