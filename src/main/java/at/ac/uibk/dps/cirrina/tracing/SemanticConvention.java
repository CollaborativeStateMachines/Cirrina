package at.ac.uibk.dps.cirrina.tracing;

public class SemanticConvention {

  // Counter names
  public static final String COUNTER_EVENTS_RECEIVED = "cirrina.events.received";
  public static final String COUNTER_EVENTS_HANDLED = "cirrina.events.handled";

  public static final String COUNTER_INVOCATIONS = "cirrina.invocations";
  public static final String COUNTER_TRANSITIONS = "cirrina.transitions";

  public static final String COUNTER_STATE_MACHINE_INSTANCES = "cirrina.state_machine_instances";

  // Counter attributes
  public static final String COUNTER_ATTR_EVENT_CHANNEL = "cirrina.event.channel";

  // Gauge names
  public static final String GAUGE_EVENT_RESPONSE_TIME_EXCLUSIVE = "cirrina.event.exclusive_response_time_ms";
  public static final String GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE = "cirrina.event.inclusive_response_time_ms";

  public static final String GAUGE_ACTION_DATA_LATENCY = "cirrina.action.data_latency_ms";
  public static final String GAUGE_ACTION_INVOKE_LATENCY = "cirrina.action.invoke_latency_ms";
  public static final String GAUGE_ACTION_RAISE_LATENCY = "cirrina.action.raise_latency_ms";

  // Gauge attributes
  public static final String GAUGE_ATTR_EVENT_CHANNEL = "cirrina.event.channel";

  public static final String GAUGE_ATTR_DATA_OPERATION = "cirrina.data.operation";
  public static final String GAUGE_ATTR_DATA_LOCALITY = "cirrina.data.locality";
  public static final String GAUGE_ATTR_DATA_SIZE = "cirrina.data.size";

  public static final String GAUGE_ATTR_INVOCATION_LOCALITY = "cirrina.invocation.locality";

  // General attributes
  public static final String ATTR_STATE_MACHINE_ID = "cirrina.state_machine.id";
  public static final String ATTR_STATE_MACHINE_NAME = "cirrina.state_machine.name";
  public static final String ATTR_ACTIVE_STATE = "cirrina.active_state";
  public static final String ATTR_PARENT_STATE_MACHINE_ID = "cirrina.parent_state_machine.id";
  public static final String ATTR_PARENT_STATE_MACHINE_NAME = "cirrina.parent_state_machine.name";
  public static final String ATTR_TRANSITION_INTERNAL = "cirrina.transition.internal";

  //Tracing Attributes
  public static final String ATTR_GUARD_EXPRESSION = "cirrina.guard.expression";
  public static final String ATTR_EVENT_NAME = "cirrina.event.name";
  public static final String ATTR_EVENT_ID = "cirrina.event.id";
  public static final String ATTR_ACTION_NAME = "cirrina.action.action_name";
  public static final String ATTR_TARGET_STATE = "cirrina.target.state";
  public static final String ATTR_SOURCE_STATE = "cirrina.source.state";
  public static final String ATTR_NEW_STATE = "cirrina.new.state";
  public static final String ATTR_OLD_STATE = "cirrina.old.state";
  public static final String ATTR_RESPONSE = "cirrina.response";
  public static final String ATTR_INVOKED_BY = "cirrina.invoked_by";
  public static final String ATTR_SERVICE_NAME = "cirrina.service_name";
  public static final String ATTR_IS_LOCAL = "cirrina.is_local";
}
