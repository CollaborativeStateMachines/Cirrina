package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ServiceImplementationSelectorTest {

  @Test
  fun testSelectMatchingServices() {
    RandomServiceImplementationSelector(
        ServiceImplementationBuilder.from(
            listOf(
              ServiceImplementationBindings.HttpServiceImplementationBinding(
                "A",
                true,
                ServiceImplementationBindings.Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                ServiceImplementationBindings.HttpMethod.GET,
              ),
              ServiceImplementationBindings.HttpServiceImplementationBinding(
                "A",
                false,
                ServiceImplementationBindings.Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                ServiceImplementationBindings.HttpMethod.GET,
              ),
              ServiceImplementationBindings.HttpServiceImplementationBinding(
                "B",
                false,
                ServiceImplementationBindings.Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                ServiceImplementationBindings.HttpMethod.GET,
              ),
              ServiceImplementationBindings.HttpServiceImplementationBinding(
                "B",
                false,
                ServiceImplementationBindings.Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                ServiceImplementationBindings.HttpMethod.GET,
              ),
              ServiceImplementationBindings.HttpServiceImplementationBinding(
                "C",
                true,
                ServiceImplementationBindings.Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                ServiceImplementationBindings.HttpMethod.GET,
              ),
            )
          )
          .build()
          .getOrThrow()
      )
      .let { serviceImplementationSelector ->
        assertDoesNotThrow {
          // Success case
          assertNotNull(
            serviceImplementationSelector.select("A", Csml.InvocationMode.REMOTE),
            "selector should find a remote implementation for service 'A'",
          )

          // Missing service case
          assertNull(serviceImplementationSelector.select("D", Csml.InvocationMode.REMOTE))
        }
      }
  }
}
