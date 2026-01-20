package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import org.hibernate.validator.internal.util.Contracts.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ServiceImplementationSelectorTest {

  @Test
  fun testSelectMatchingServices() {
    // Define service bindings using idiomatic Kotlin list creation
    val serviceBindings =
      listOf(
        // Service A (Local)
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
        // Service A (Remote)
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
        // Service B (Remote)
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
        // Service B (Remote duplicate)
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
        // Service C (Local)
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

    // Builders return Result, so we unwrap here for the test setup
    val services = ServiceImplementationBuilder.from(serviceBindings).build().getOrThrow()

    val serviceSelector = RandomServiceImplementationSelector(services)

    assertDoesNotThrow {
      val selected = serviceSelector.select("A", Csml.InvocationMode.REMOTE)
      assertNotNull(selected, "Selector should find a remote implementation for service 'A'")
    }
  }
}
