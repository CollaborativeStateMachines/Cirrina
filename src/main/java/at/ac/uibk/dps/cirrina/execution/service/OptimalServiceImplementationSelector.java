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
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

public class OptimalServiceImplementationSelector extends ServiceImplementationSelector {

  /**
   * Initializes this service implementation selector.
   *
   * @param serviceImplementations Known service implementations.
   */
  public OptimalServiceImplementationSelector(Multimap<String, ServiceImplementation> serviceImplementations) {
    super(serviceImplementations);
  }

  /**
   * Selects, given the known service implementations, the service implementation that minimizes cost and maximizes performance. If a local
   * service implementation is requested, the selected service implementation is required to be a local service implementation.
   *
   * @param name  Name of the requested service implementation.
   * @param local Whether the local implementation is required to be a local service implementation.
   * @return Selected service implementation.
   */
  @Override
  public Optional<ServiceImplementation> select(String name, boolean local, TracingAttributes tracingAttributes, Span parentSpan) {
    Span span = tracing.initializeSpan("Optimal Service Selection", tracer, parentSpan,
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

      final var maxCost = serviceImplementationsWithName.stream().mapToDouble(ServiceImplementation::getCost).max().orElse(1);
      final var maxPerformance = serviceImplementationsWithName.stream().mapToDouble(ServiceImplementation::getPerformance).max().orElse(1);

      final var costs = serviceImplementationsWithName.stream()
          .map(serviceImplementation -> serviceImplementation.getCost() / maxCost)
          .toList();

      final var performances = serviceImplementationsWithName.stream()
          .map(serviceImplementation -> serviceImplementation.getPerformance() / maxPerformance)
          .toList();

      final var selectedIndex = IntStream.range(0, serviceImplementationsWithName.size())
          .boxed()
          .min(Comparator.comparingDouble(i -> costs.get(i) / performances.get(i)))
          .get();

      return Optional.of(serviceImplementationsWithName.get(selectedIndex));
    } finally {
      span.end();
    }
  }
}
