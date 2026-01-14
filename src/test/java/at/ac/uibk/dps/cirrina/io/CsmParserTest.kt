package at.ac.uibk.dps.cirrina.io

import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.utils.TestUtils.resourceUri
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CsmParserTest {

  @Test
  fun testCsmlPositive() {
    assertDoesNotThrow { CsmParser.parseCsml(DefaultDescriptions.complete) }
  }

  @Test
  fun testCsmlNegative() {
    assertThrows(IllegalArgumentException::class.java) {
      CsmParser.parseCsml(DefaultDescriptions.empty)
    }
  }

  @Test
  fun testCsmlUri() {
    assertDoesNotThrow {
      CsmParser.parseCsml(
        URI(
          "https://raw.githubusercontent.com/CollaborativeStateMachines/Cirrina/refs/heads/develop/src/test/resources/pkl/noop/main.pkl"
        )
      )
    }
  }

  @Test
  fun loadServiceImplementationBindingsUri() {
    assertDoesNotThrow {
      CsmParser.parseServiceImplementationBindings(
        URI(
          "https://raw.githubusercontent.com/CollaborativeStateMachines/Cirrina/refs/heads/develop/src/test/resources/pkl/serviceImplementation/services.pkl"
        )
      )
    }
  }

  @Test
  fun loadServiceImplementationBindings() {
    assertDoesNotThrow {
      val services =
        CsmParser.parseServiceImplementationBindings(
          resourceUri("pkl/serviceImplementation/services.pkl")
        )

      assertEquals(2, services.bindings.size)

      val service1 =
        services.bindings[0] as ServiceImplementationBindings.HttpServiceImplementationBinding
      assertEquals("myHttpService1", service1.name)
      assertEquals(false, service1.isLocal)
      assertEquals("https", service1.scheme)
      assertEquals("api.example.com", service1.host)
      assertEquals(443, service1.port)
      assertEquals("/data", service1.endPoint)
      assertEquals(ServiceImplementationBindings.HttpMethod.GET, service1.method)

      val service2 =
        services.bindings[1] as ServiceImplementationBindings.HttpServiceImplementationBinding
      assertEquals("myHttpService2", service2.name)
      assertEquals(false, service2.isLocal)
      assertEquals("https", service2.scheme)
      assertEquals("api.example.com", service2.host)
      assertEquals(443, service2.port)
      assertEquals("/data", service2.endPoint)
      assertEquals(ServiceImplementationBindings.HttpMethod.GET, service2.method)
    }
  }
}
