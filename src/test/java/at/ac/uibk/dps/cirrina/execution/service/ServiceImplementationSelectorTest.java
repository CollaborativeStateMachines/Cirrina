package at.ac.uibk.dps.cirrina.execution.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.HttpMethod;
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.HttpServiceImplementationBinding;
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.ServiceImplementationBinding;
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceImplementationSelectorTest {

  @Test
  void testSelectMatchingServices() {
    final var serviceBindings = new ServiceImplementationBinding[5];

    // Service one
    {
      final var service = new HttpServiceImplementationBinding(
        "A",
        true,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        HttpMethod.GET
      );

      serviceBindings[0] = service;
    }

    // Service two
    {
      final var service = new HttpServiceImplementationBinding(
        "A",
        false,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        HttpMethod.GET
      );

      serviceBindings[1] = service;
    }

    // Service three
    {
      final var service = new HttpServiceImplementationBinding(
        "B",
        false,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        HttpMethod.GET
      );

      serviceBindings[2] = service;
    }

    // Service four
    {
      final var service = new HttpServiceImplementationBinding(
        "B",
        false,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        HttpMethod.GET
      );

      serviceBindings[3] = service;
    }

    // Service five
    {
      final var service = new HttpServiceImplementationBinding(
        "C",
        true,
        Type.HTTP,
        "http",
        "localhost",
        12345,
        "",
        HttpMethod.GET
      );

      serviceBindings[4] = service;
    }

    final var services = ServiceImplementationBuilder.from(List.of(serviceBindings)).build();

    final var serviceSelector = new RandomServiceImplementationSelector(services);

    assertDoesNotThrow(() -> {
      var selected = serviceSelector.select("A", false);
    });

    // TODO: Add additional tests
  }
}
