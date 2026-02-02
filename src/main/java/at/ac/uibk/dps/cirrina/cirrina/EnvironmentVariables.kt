package at.ac.uibk.dps.cirrina.cirrina

enum class EventProvider {
  ZENOH
}

enum class PersistentContextProvider {
  ETCD
}

data class EnvironmentVariable<T>(
  val name: String,
  val default: T,
  val mapper: (String) -> T = { it as T },
) {
  fun get(): T {
    val value = System.getenv(name)
    return when {
      value != null -> mapper(value)
      else -> default
    }
  }
}

object EnvironmentVariables {
  // TODO: Make nullable
  val etcdContextUrl = EnvironmentVariable("ETCD_CONTEXT_URL", "http://localhost:2379")

  // TODO: Make nullable
  val influxMetricUrl = EnvironmentVariable("INFLUX_METRIC_URL", "http://localhost:8086")

  val influxMetricOrg = EnvironmentVariable("INFLUX_METRIC_ORG", "org")
  val influxMetricBucket = EnvironmentVariable("INFLUX_METRIC_BUCKET", "bucket")
  val influxMetricToken = EnvironmentVariable("INFLUX_METRIC_TOKEN", "bzO10KmR8x")
  val influxMetricStep = EnvironmentVariable("INFLUX_METRIC_STEP", 5000L)

  // TODO: Make nullable
  val zipkinTraceUrl = EnvironmentVariable("ZIPKIN_TRACE_URL", "http://localhost:9411/api/v2/spans")

  val csmMainUri = EnvironmentVariable("CSM_MAIN_URI", "file:///app/main.pkl")

  val csmServiceBindingsUri =
    EnvironmentVariable("CSM_SERVICE_BINDINGS_URL", "file:///app/services.pkl")

  val eventProvider =
    EnvironmentVariable(
      "EVENT_PROVIDER",
      EventProvider.ZENOH,
      { value ->
        try {
          EventProvider.valueOf(value.uppercase())
        } catch (_: Exception) {
          error("invalid value for environment variable EVENT_PROVIDER")
        }
      },
    )

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
