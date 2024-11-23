package at.ac.uibk.dps.cirrina.execution.service;

import static at.ac.uibk.dps.cirrina.cirrina.Cirrina.tracer;
import static at.ac.uibk.dps.cirrina.cirrina.Cirrina.tracing;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*;

import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class RandomServiceImplementationSelector extends ServiceImplementationSelector {

  /**
   * Initializes this service implementation selector.
   *
   * @param serviceImplementations Known service implementations.
   */
  public RandomServiceImplementationSelector(Multimap<String, ServiceImplementation> serviceImplementations) {
    super(serviceImplementations);
  }

  /**
   * Selects, given the known service implementations, a random matching service implementation.
   *
   * @param name  Name of the requested service implementation.
   * @param local Whether the local implementation is required to be a local service implementation.
   * @return Selected service implementation.
   */
  @Override
  public Optional<ServiceImplementation> select(String name, boolean local, TracingAttributes tracingAttributes, Span parentSpan) {
    Span span = tracing.initializeSpan("Random Service Selection", tracer, parentSpan,
        Map.of(ATTR_SERVICE_NAME, name,
            ATTR_IS_LOCAL, String.valueOf(local),
            ATTR_STATE_MACHINE_ID, tracingAttributes.getStateMachineId(),
            ATTR_STATE_MACHINE_NAME, tracingAttributes.getStateMachineName(),
            ATTR_PARENT_STATE_MACHINE_ID, tracingAttributes.getParentStateMachineId(),
            ATTR_PARENT_STATE_MACHINE_NAME, tracingAttributes.getParentStateMachineName()));

    try (Scope scope = span.makeCurrent()) {

      final var serviceImplementationsWithName = new ArrayList<ServiceImplementation>(local ?
          Multimaps.filterValues(serviceImplementations, ServiceImplementation::isLocal).get(name) :
          serviceImplementations.get(name));

      if (serviceImplementationsWithName.isEmpty()) {
        return Optional.empty();
      }

      ServiceImplementation randomImplementation = serviceImplementationsWithName.get(
          new Random().nextInt(serviceImplementationsWithName.size()));

      return Optional.of(randomImplementation);
    } finally {
      span.end();
    }
  }
}
