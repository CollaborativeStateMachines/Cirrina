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
  val default: T,
  val mapper: (String) -> T = { it as T },
) {
  /** Get the value of the environment variable. */
  fun get(): T {
    val value = System.getenv(name)
    return when {
      value != null -> mapper(value)
      else -> default
    }
  }
}

/** Common interface for acquiring environment variables. */
object EnvironmentVariables {
  /** The NATS event server URL. */
  val natsEventUrl = EnvironmentVariable("NATS_EVENT_URL", "nats://localhost:4222/")

  /** The Etcd context server URL. */
  val etcdContextUrl = EnvironmentVariable("ETCD_CONTEXT_URL", "http://localhost:2379")

  /** InfluxDB metric variables. */
  val influxMetricUrl = EnvironmentVariable("INFLUX_METRIC_URL", "http://localhost:8086")

  val influxMetricOrg = EnvironmentVariable("INFLUX_METRIC_ORG", "org")
  val influxMetricBucket = EnvironmentVariable("INFLUX_METRIC_BUCKET", "bucket")
  val influxMetricToken = EnvironmentVariable("INFLUX_METRIC_TOKEN", "bzO10KmR8x")
  val influxMetricStep = EnvironmentVariable("INFLUX_METRIC_STEP", 5000L)

  /** The path to the CSML application. */
  val csmMainUri = EnvironmentVariable("CSM_MAIN_URI", "file:///app/main.pkl")

  /** The path to the service implementation bindings. */
  val csmServiceBindingsUri =
    EnvironmentVariable("CSM_SERVICE_BINDINGS_URL", "file:///app/services.pkl")

  /** The event provider to use. */
  val eventProvider =
    EnvironmentVariable(
      "EVENT_PROVIDER",
      EventProvider.NATS,
      { value ->
        try {
          EventProvider.valueOf(value.uppercase())
        } catch (_: Exception) {
          error("invalid value for environment variable EVENT_PROVIDER")
        }
      },
    )

  /** The context provider to use. */
  val contextProvider =
    EnvironmentVariable(
      "CONTEXT_PROVIDER",
      PersistentContextProvider.ETCD,
      { value ->
        try {
          PersistentContextProvider.valueOf(value.uppercase())
        } catch (_: Exception) {
          error("invalid value for environment variable CONTEXT_PROVIDER")
        }
      },
    )
}
