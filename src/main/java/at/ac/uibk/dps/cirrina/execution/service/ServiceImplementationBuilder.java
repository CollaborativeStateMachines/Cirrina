package at.ac.uibk.dps.cirrina.execution.service;

import at.ac.uibk.dps.cirrina.csm.description.HttpServiceImplementationDescription;
import at.ac.uibk.dps.cirrina.csm.description.ServiceImplementationDescription;
import at.ac.uibk.dps.cirrina.execution.service.HttpServiceImplementation.Parameters;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;

/**
 * Service implementation builder, builds service implementation objects.
 */
public class ServiceImplementationBuilder {

  private final List<? extends ServiceImplementationDescription> serviceImplementationDescriptions;

  private ServiceImplementationBuilder(
    List<? extends ServiceImplementationDescription> serviceImplementationDescriptions
  ) {
    this.serviceImplementationDescriptions = serviceImplementationDescriptions;
  }

  /**
   * Construct a builder from a service implementation description.
   *
   * @param serviceImplementationDescription Service implementation description.
   * @return Builder.
   */
  public static ServiceImplementationBuilder from(
    ServiceImplementationDescription serviceImplementationDescription
  ) {
    var list = new ArrayList<ServiceImplementationDescription>();
    list.add(serviceImplementationDescription);
    return new ServiceImplementationBuilder(list);
  }

  /**
   * Construct a builder from multiple service implementation descriptions.
   *
   * @param serviceImplementationDescriptions Service implementation descriptions.
   * @return Builder.
   */
  public static ServiceImplementationBuilder from(
    List<? extends ServiceImplementationDescription> serviceImplementationDescriptions
  ) {
    return new ServiceImplementationBuilder(serviceImplementationDescriptions);
  }

  /**
   * Builds the service implementation.
   *
   * @return Service implementation.
   */
  private static ServiceImplementation buildOne(
    ServiceImplementationDescription serviceImplementationDescription
  ) {
    switch (serviceImplementationDescription) {
      case HttpServiceImplementationDescription s -> {
        return new HttpServiceImplementation(
          new Parameters(
            s.getName(),
            s.getCost(),
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
        String.format("Unexpected value: %s", serviceImplementationDescription.getType())
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

    for (var serviceDescription : serviceImplementationDescriptions) {
      var service = buildOne(serviceDescription);

      services.put(service.getName(), service);
    }

    return services;
  }
}
