package at.ac.uibk.dps.cirrina.execution.service;

import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.HttpServiceImplementationBinding;
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.ServiceImplementationBinding;
import at.ac.uibk.dps.cirrina.execution.service.HttpServiceImplementation.Parameters;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;

/**
 * Service implementation builder, builds service implementation objects.
 */
public class ServiceImplementationBuilder {

  private final List<? extends ServiceImplementationBinding> serviceImplementationBindings;

  private ServiceImplementationBuilder(
    List<? extends ServiceImplementationBinding> serviceImplementationBindings
  ) {
    this.serviceImplementationBindings = serviceImplementationBindings;
  }

  /**
   * Construct a builder from service implementation bindings.
   *
   * @param serviceImplementationBinding Service implementation binding.
   * @return Builder.
   */
  public static ServiceImplementationBuilder from(
    ServiceImplementationBinding serviceImplementationBinding
  ) {
    var list = new ArrayList<ServiceImplementationBinding>();
    list.add(serviceImplementationBinding);
    return new ServiceImplementationBuilder(list);
  }

  /**
   * Construct a builder from multiple service implementation bindings.
   *
   * @param serviceImplementationBindings Service implementation bindings.
   * @return Builder.
   */
  public static ServiceImplementationBuilder from(
    List<? extends ServiceImplementationBinding> serviceImplementationBindings
  ) {
    return new ServiceImplementationBuilder(serviceImplementationBindings);
  }

  /**
   * Builds the service implementation.
   *
   * @return Service implementation.
   */
  private static ServiceImplementation buildOne(
    ServiceImplementationBinding serviceImplementationBinding
  ) {
    switch (serviceImplementationBinding) {
      case HttpServiceImplementationBinding s -> {
        return new HttpServiceImplementation(
          new Parameters(
            s.getName(),
            s.isLocal(),
            s.getScheme(),
            s.getHost(),
            s.getPort(),
            s.getEndPoint(),
            s.getMethod()
          )
        );
      }
      default -> throw new IllegalStateException(
        String.format("Unexpected value: %s", serviceImplementationBinding.getType())
      );
    }
  }

  /**
   * Builds the service implementations.
   *
   * @return Service implementations.
   */
  public Multimap<String, ServiceImplementation> build() {
    Multimap<String, ServiceImplementation> services = ArrayListMultimap.create();

    for (var serviceDescription : serviceImplementationBindings) {
      var service = buildOne(serviceDescription);

      services.put(service.getName(), service);
    }

    return services;
  }
}
