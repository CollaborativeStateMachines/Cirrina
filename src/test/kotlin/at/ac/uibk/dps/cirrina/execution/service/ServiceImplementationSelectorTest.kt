package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml.HttpMethod
import at.ac.uibk.dps.cirrina.csm.Csml.HttpServiceImplementationBinding
import at.ac.uibk.dps.cirrina.csm.Csml.InvocationMode
import at.ac.uibk.dps.cirrina.csm.Csml.Type
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ServiceImplementationSelectorTest {

  @Test
  fun testSelectMatchingServices() {
    RandomServiceImplementationSelector(
        ServiceImplementation.from(
            listOf(
              HttpServiceImplementationBinding(
                "A",
                true,
                Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                HttpMethod.GET,
              ),
              HttpServiceImplementationBinding(
                "A",
                false,
                Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                HttpMethod.GET,
              ),
              HttpServiceImplementationBinding(
                "B",
                false,
                Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                HttpMethod.GET,
              ),
              HttpServiceImplementationBinding(
                "B",
                false,
                Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                HttpMethod.GET,
              ),
              HttpServiceImplementationBinding(
                "C",
                true,
                Type.HTTP,
                "http",
                "localhost",
                12345,
                "",
                HttpMethod.GET,
              ),
            )
          )
          .getOrThrow()
      )
      .let { serviceImplementationSelector ->
        assertDoesNotThrow {
          // Success case
          assertNotNull(
            serviceImplementationSelector.select("A", InvocationMode.REMOTE),
            "selector should find a remote implementation for service 'A'",
          )

          // Missing service case
          assertNull(serviceImplementationSelector.select("D", InvocationMode.REMOTE))
        }
      }
  }
}
