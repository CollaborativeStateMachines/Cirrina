package at.ac.uibk.dps.cirrina.execution.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import at.ac.uibk.dps.cirrina.csm.description.HttpServiceImplementationDescription;
import at.ac.uibk.dps.cirrina.csm.description.HttpServiceImplementationDescription.Method;
import at.ac.uibk.dps.cirrina.csm.description.ServiceImplementationDescription;
import at.ac.uibk.dps.cirrina.csm.description.ServiceImplementationDescription.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceImplementationSelectorTest {

  @Test
  void testSelectMatchingServices() {
    final var serviceDescriptions = new ServiceImplementationDescription[5];

    // Service one
    {
      final var service = new HttpServiceImplementationDescription(
        "A",
        1.0,
        true,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        Method.GET
      );

      serviceDescriptions[0] = service;
    }

    // Service two
    {
      final var service = new HttpServiceImplementationDescription(
        "A",
        0.5,
        false,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        Method.GET
      );

      serviceDescriptions[1] = service;
    }

    // Service three
    {
      final var service = new HttpServiceImplementationDescription(
        "B",
        0.4,
        false,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        Method.GET
      );

      serviceDescriptions[2] = service;
    }

    // Service four
    {
      final var service = new HttpServiceImplementationDescription(
        "B",
        0.2,
        false,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        Method.GET
      );

      serviceDescriptions[3] = service;
    }

    // Service five
    {
      final var service = new HttpServiceImplementationDescription(
        "C",
        1.0,
        true,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        Method.GET
      );

      serviceDescriptions[4] = service;
    }

    final var services = ServiceImplementationBuilder.from(List.of(serviceDescriptions)).build();

    final var serviceSelector = new OptimalServiceImplementationSelector(services);

    assertDoesNotThrow(() -> {
      var selected = serviceSelector.select("A", false);

      assertEquals(0.5f, selected.get().getCost(), 0.0001);
    });

    // TODO: Add additional tests
  }
}
