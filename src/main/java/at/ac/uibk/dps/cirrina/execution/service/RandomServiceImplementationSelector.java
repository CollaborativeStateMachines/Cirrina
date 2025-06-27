package at.ac.uibk.dps.cirrina.execution.service;

import at.ac.uibk.dps.cirrina.observability.tracing.MethodName;
import at.ac.uibk.dps.cirrina.observability.tracing.Trace;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.util.ArrayList;
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
  @Trace(name = MethodName.SELECT)
  @Override
  public Optional<ServiceImplementation> select(String name, boolean local) {
    final var serviceImplementationsWithName = new ArrayList<ServiceImplementation>(local ?
        Multimaps.filterValues(serviceImplementations, ServiceImplementation::isLocal).get(name) :
        serviceImplementations.get(name));

    if (serviceImplementationsWithName.isEmpty()) {
      return Optional.empty();
    }

    ServiceImplementation randomImplementation = serviceImplementationsWithName.get(
        new Random().nextInt(serviceImplementationsWithName.size()));

    return Optional.of(randomImplementation);
  }
}
