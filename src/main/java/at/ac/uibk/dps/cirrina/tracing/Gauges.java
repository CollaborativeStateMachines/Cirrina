package at.ac.uibk.dps.cirrina.tracing;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_STATE_MACHINE_ID;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ATTR_DATA_LOCALITY;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ATTR_DATA_OPERATION;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ATTR_DATA_SIZE;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ATTR_EVENT_CHANNEL;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ATTR_INVOCATION_LOCALITY;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.Meter;
import java.util.HashMap;
import java.util.Map;

public class Gauges {

  private final Map<String, DoubleGauge> gauges = new HashMap<>();

  private final Meter meter;

  private final String stateMachineId;

  public Gauges(Meter meter, String stateMachineId) {
    this.meter = meter;
    this.stateMachineId = stateMachineId;
  }

  public Attributes attributesForData(String operation, String dataLocality, int dataSize) {
    return Attributes.builder()
      .put(GAUGE_ATTR_DATA_OPERATION, operation)
      .put(GAUGE_ATTR_DATA_LOCALITY, dataLocality)
      .put(GAUGE_ATTR_DATA_SIZE, dataSize)
      .put(ATTR_STATE_MACHINE_ID, stateMachineId)
      .build();
  }

  public Attributes attributesForInvocation(String serviceLocality) {
    return Attributes.builder()
      .put(GAUGE_ATTR_INVOCATION_LOCALITY, serviceLocality)
      .put(ATTR_STATE_MACHINE_ID, stateMachineId)
      .build();
  }

  public Attributes attributesForEvent(String eventChannel) {
    return Attributes.builder()
      .put(GAUGE_ATTR_EVENT_CHANNEL, eventChannel)
      .put(ATTR_STATE_MACHINE_ID, stateMachineId)
      .build();
  }

  public void addGauge(String name) {
    gauges.put(name, meter.gaugeBuilder(name).build());
  }

  public DoubleGauge getGauge(String name) {
    return gauges.get(name);
  }
}
