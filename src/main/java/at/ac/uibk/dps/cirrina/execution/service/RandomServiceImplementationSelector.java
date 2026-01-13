package at.ac.uibk.dps.cirrina.execution.service;

import at.ac.uibk.dps.cirrina.csm.Csml.InvocationMode;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;

public class RandomServiceImplementationSelector extends ServiceImplementationSelector {

  /**
   * Initializes this service implementation selector.
   *
   * @param serviceImplementations known service implementations
   */
  public RandomServiceImplementationSelector(
    Multimap<String, ServiceImplementation> serviceImplementations
  ) {
    super(serviceImplementations);
  }

  /**
   * Selects, given the known service implementations, a random matching service implementation.
   *
   * @param name name of the requested service implementation
   * @param mode the invocation mode
   * @return selected service implementation
   */
  @Override
  public Optional<ServiceImplementation> select(String name, InvocationMode mode) {
    final var serviceImplementationsWithName = new ArrayList<>(
      mode == InvocationMode.LOCAL
        ? Multimaps.filterValues(
          serviceImplementations,
          serviceImplementation -> serviceImplementation != null && serviceImplementation.isLocal()
        ).get(name)
        : serviceImplementations.get(name)
    );

    if (serviceImplementationsWithName.isEmpty()) {
      return Optional.empty();
    }

    ServiceImplementation randomImplementation = serviceImplementationsWithName.get(
      new Random().nextInt(serviceImplementationsWithName.size())
    );

    return Optional.of(randomImplementation);
  }
}
