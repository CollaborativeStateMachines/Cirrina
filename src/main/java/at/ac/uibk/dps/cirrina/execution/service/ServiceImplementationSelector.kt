package at.ac.uibk.dps.cirrina.execution.service;

import at.ac.uibk.dps.cirrina.csm.Csml.InvocationMode;
import com.google.common.collect.Multimap;
import java.util.Optional;

public abstract class ServiceImplementationSelector {

  protected final Multimap<String, ServiceImplementation> serviceImplementations;

  /**
   * Initializes this service implementation selector.
   *
   * @param serviceImplementations known service implementations
   */
  public ServiceImplementationSelector(
    Multimap<String, ServiceImplementation> serviceImplementations
  ) {
    this.serviceImplementations = serviceImplementations;
  }

  /**
   * Selects, given the known service implementations, a matching service implementation.
   *
   * @param name name of the requested service implementation
   * @param mode the invocation mode
   * @return selected service implementation
   */
  public abstract Optional<ServiceImplementation> select(String name, InvocationMode mode);
}
