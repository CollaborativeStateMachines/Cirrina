package at.ac.uibk.dps.cirrina.tracing;

public class SemanticConvention {

  // Counter names
  public static final String COUNTER_EVENTS_RECEIVED = "cirrina.events.received";
  public static final String COUNTER_EVENTS_HANDLED = "cirrina.events.handled";

  public static final String COUNTER_INVOCATIONS = "cirrina.invocations";

  public static final String COUNTER_STATE_MACHINE_INSTANCES = "cirrina.state_machine_instances";

  // Counter attributes
  public static final String COUNTER_ATTR_EVENT_CHANNEL = "cirrina.event.channel";

  // Gauge names
  public static final String GAUGE_EVENT_RESPONSE_TIME_EXCLUSIVE =
    "cirrina.event.exclusive_response_time_ms";
  public static final String GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE =
    "cirrina.event.inclusive_response_time_ms";

  public static final String GAUGE_ACTION_DATA_LATENCY = "cirrina.action.data_latency_ms";
  public static final String GAUGE_ACTION_INVOKE_LATENCY = "cirrina.action.invoke_latency_ms";
  public static final String GAUGE_ACTION_RAISE_LATENCY = "cirrina.action.raise_latency_ms";
  public static final String GAUGE_ACTION_EVAL_LATENCY = "cirrina.action.eval_latency_ms";

  // Gauge attributes
  public static final String GAUGE_ATTR_EVENT_CHANNEL = "cirrina.event.channel";

  public static final String GAUGE_ATTR_INVOCATION_LOCALITY = "cirrina.invocation.locality";

  // General attributes
  public static final String ATTR_STATE_MACHINE_ID = "cirrina.state_machine.id";
}
