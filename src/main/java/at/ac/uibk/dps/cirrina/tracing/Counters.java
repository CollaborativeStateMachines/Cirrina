package at.ac.uibk.dps.cirrina.tracing;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_ACTIVE_STATE;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_PARENT_STATE_MACHINE_ID;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_PARENT_STATE_MACHINE_NAME;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_STATE_MACHINE_ID;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_STATE_MACHINE_NAME;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.COUNTER_ATTR_EVENT_CHANNEL;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_TRANSITION_INTERNAL;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.HashMap;
import java.util.Map;

public class Counters {

  private final Map<String, LongUpDownCounter> counters = new HashMap<>();

  private final Meter meter;

  private final String stateMachineId;

  private final String stateMachineName;

  private final String parentStateMachineId;

  private final String parentStateMachineName;

  public Counters(Meter meter, String stateMachineId, String parentStateMachineId, String stateMachineName, String parentStateMachineName) {
    this.meter = meter;
    this.stateMachineId = stateMachineId;
    this.parentStateMachineId = parentStateMachineId;
    this.stateMachineName = stateMachineName;
    this.parentStateMachineName = parentStateMachineName;
  }

  public Attributes attributesForEvent(String eventChannel, String activeState) {
    return Attributes.builder()
        .put(COUNTER_ATTR_EVENT_CHANNEL, eventChannel)
        .put(ATTR_STATE_MACHINE_ID, stateMachineId)
        .put(ATTR_STATE_MACHINE_NAME, stateMachineName)
        .put(ATTR_PARENT_STATE_MACHINE_ID, parentStateMachineId)
        .put(ATTR_PARENT_STATE_MACHINE_NAME, parentStateMachineName)
        .put(ATTR_ACTIVE_STATE, activeState)
        .build();
  }

  public Attributes attributesForInvocation() {
    return Attributes.builder()
        .put(ATTR_STATE_MACHINE_ID, stateMachineId)
        .put(ATTR_STATE_MACHINE_NAME, stateMachineName)
        .put(ATTR_PARENT_STATE_MACHINE_ID, parentStateMachineId)
        .put(ATTR_PARENT_STATE_MACHINE_NAME, parentStateMachineName)
        .build();
  }

  public Attributes attributesForInstances() {
    return Attributes.builder()
        .put(ATTR_STATE_MACHINE_ID, stateMachineId)
        .put(ATTR_STATE_MACHINE_NAME, stateMachineName)
        .put(ATTR_PARENT_STATE_MACHINE_ID, parentStateMachineId)
        .put(ATTR_PARENT_STATE_MACHINE_NAME, parentStateMachineName)
        .build();
  }

  public Attributes attributesForTransition(Boolean isInternal) {
    return Attributes.builder()
        .put(ATTR_TRANSITION_INTERNAL, isInternal)
        .put(ATTR_STATE_MACHINE_ID, stateMachineId)
        .put(ATTR_STATE_MACHINE_NAME, stateMachineName)
        .put(ATTR_PARENT_STATE_MACHINE_ID, parentStateMachineId)
        .put(ATTR_PARENT_STATE_MACHINE_NAME, parentStateMachineName)
        .build();
  }

  public void addCounter(String name) {
    counters.put(name, meter.upDownCounterBuilder(name).build());
  }

  public LongUpDownCounter getCounter(String name) {
    return counters.get(name);
  }
}
