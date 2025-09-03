package at.ac.uibk.dps.cirrina.tracing;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_STATE_MACHINE_ID;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.COUNTER_ATTR_EVENT_CHANNEL;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.HashMap;
import java.util.Map;

public class Counters {

  private final Map<String, LongUpDownCounter> counters = new HashMap<>();

  private final Meter meter;

  private final String stateMachineId;

  public Counters(Meter meter, String stateMachineId) {
    this.meter = meter;
    this.stateMachineId = stateMachineId;
  }

  public Attributes attributesForEvent(String eventChannel) {
    return Attributes.builder()
      .put(COUNTER_ATTR_EVENT_CHANNEL, eventChannel)
      .put(ATTR_STATE_MACHINE_ID, stateMachineId)
      .build();
  }

  public Attributes attributesForInvocation() {
    return Attributes.builder().put(ATTR_STATE_MACHINE_ID, stateMachineId).build();
  }

  public Attributes attributesForInstances() {
    return Attributes.builder().build();
  }

  public void addCounter(String name) {
    counters.put(name, meter.upDownCounterBuilder(name).build());
  }

  public LongUpDownCounter getCounter(String name) {
    return counters.get(name);
  }
}
