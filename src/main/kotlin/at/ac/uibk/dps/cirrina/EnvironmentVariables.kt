package at.ac.uibk.dps.cirrina

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
  val etcdContextUrl = EnvironmentVariable<String?>("ETCD_CONTEXT_URL", null)

  val zenohEventHandlerConfigUri =
    EnvironmentVariable<String?>("ZENOH_EVENT_HANDLER_CONFIG_URI", null)

  val influxMetricUrl = EnvironmentVariable<String?>("INFLUX_METRIC_URL", null)

  val influxMetricOrg = EnvironmentVariable("INFLUX_METRIC_ORG", "org")
  val influxMetricBucket = EnvironmentVariable("INFLUX_METRIC_BUCKET", "bucket")
  val influxMetricToken = EnvironmentVariable("INFLUX_METRIC_TOKEN", "bzO10KmR8x")
  val influxMetricStep = EnvironmentVariable("INFLUX_METRIC_STEP", 5000L, { it.toLong() })

  val zipkinTraceUrl = EnvironmentVariable<String?>("ZIPKIN_TRACE_URL", null)

  val csmMainUri = EnvironmentVariable("CSM_MAIN_URI", "file:///app/main.pkl")

  val csmGroup = EnvironmentVariable("CSM_GROUP", "cirrina")

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
