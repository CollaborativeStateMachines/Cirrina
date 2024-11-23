package at.ac.uibk.dps.cirrina.execution.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import at.ac.uibk.dps.cirrina.csml.description.HttpServiceImplementationDescription;
import at.ac.uibk.dps.cirrina.csml.description.HttpServiceImplementationDescription.Method;
import at.ac.uibk.dps.cirrina.csml.description.ServiceImplementationDescription;
import at.ac.uibk.dps.cirrina.csml.description.ServiceImplementationDescription.ServiceImplementationType;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceImplementationSelectorTest {

  private final TracingAttributes attributes = new TracingAttributes("test","test",
      "test","test");


  @Test
  void testSelectMatchingServices() {
    final var serviceDescriptions = new ServiceImplementationDescription[5];

    // Service one
    {
      final var service = new HttpServiceImplementationDescription("A", 1.0, true, ServiceImplementationType.HTTP, "http", "localhost",
          12345, "", Method.GET);

      serviceDescriptions[0] = service;
    }

    // Service two
    {
      final var service = new HttpServiceImplementationDescription("A", 0.5, false, ServiceImplementationType.HTTP, "http", "localhost",
          12345, "", Method.GET);

      serviceDescriptions[1] = service;
    }

    // Service three
    {
      final var service = new HttpServiceImplementationDescription("B", 0.4, false, ServiceImplementationType.HTTP, "http", "localhost",
          12345, "", Method.GET);

      serviceDescriptions[2] = service;
    }

    // Service four
    {
      final var service = new HttpServiceImplementationDescription("B", 0.2, false, ServiceImplementationType.HTTP, "http", "localhost",
          12345, "", Method.GET);

      serviceDescriptions[3] = service;
    }

    // Service five
    {
      final var service = new HttpServiceImplementationDescription("C", 1.0, true, ServiceImplementationType.HTTP, "http", "localhost",
          12345, "", Method.GET);

      serviceDescriptions[4] = service;
    }

    final var services = ServiceImplementationBuilder.from(List.of(serviceDescriptions)).build();

    final var serviceSelector = new OptimalServiceImplementationSelector(services);

    assertDoesNotThrow(() -> {
      var selected = serviceSelector.select("A", false, attributes, null);

      assertEquals(0.5f, selected.get().getCost(), 0.0001);
    });

    // TODO: Add additional tests
  }
}
