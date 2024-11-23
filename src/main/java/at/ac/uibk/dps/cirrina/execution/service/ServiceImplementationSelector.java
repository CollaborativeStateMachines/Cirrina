package at.ac.uibk.dps.cirrina.execution.service;

import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import com.google.common.collect.Multimap;
import io.opentelemetry.api.trace.Span;
import java.util.Optional;

public abstract class ServiceImplementationSelector {

  protected final Multimap<String, ServiceImplementation> serviceImplementations;

  /**
   * Initializes this service implementation selector.
   *
   * @param serviceImplementations Known service implementations.
   */
  public ServiceImplementationSelector(Multimap<String, ServiceImplementation> serviceImplementations) {
    this.serviceImplementations = serviceImplementations;
  }

  /**
   * Selects, given the known service implementations, a matching service implementation.
   *
   * @param name  Name of the requested service implementation.
   * @param local Whether the local implementation is required to be a local service implementation.
   * @return Selected service implementation.
   */
  public abstract Optional<ServiceImplementation> select(String name, boolean local, TracingAttributes tracingAttributes, Span span);
}
