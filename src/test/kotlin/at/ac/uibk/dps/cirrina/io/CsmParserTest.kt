package at.ac.uibk.dps.cirrina.io

import at.ac.uibk.dps.cirrina.csm.Csml.HttpMethod
import at.ac.uibk.dps.cirrina.csm.Csml.HttpServiceImplementationBinding
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.util.TestUtils.resourceUri
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CsmParserTest {

  @Test
  fun testCsmlPositive() {
    CsmParser.parseCsml(DefaultDescriptions.complete)
  }

  @Test
  fun testCsmlNegative() {
    assertThrows<Exception> { CsmParser.parseCsml(DefaultDescriptions.empty) }
  }

  @Test
  fun testCsmlUri() {
    CsmParser.parseCsml(
      URI(
        "https://raw.githubusercontent.com/CollaborativeStateMachines/Cirrina/refs/heads/develop/src/test/resources/pkl/noop/main.pkl"
      )
    )
  }

  @Test
  fun testServiceImplementationBindings() {
    val services = CsmParser.parseCsml(resourceUri("pkl/noop/main.pkl"))

    assertEquals(2, services.bindings.size)

    // Verify the first binding
    (services.bindings[0] as HttpServiceImplementationBinding).assertHttpBinding(
      expectedName = "myHttpService1",
      expectedEndpoint = "/data",
    )

    // Verify the second binding
    (services.bindings[1] as HttpServiceImplementationBinding).assertHttpBinding(
      expectedName = "myHttpService2",
      expectedEndpoint = "/data",
    )
  }

  private fun HttpServiceImplementationBinding.assertHttpBinding(
    expectedName: String,
    expectedEndpoint: String,
  ) {
    assertEquals(expectedName, name)
    assertEquals(false, isLocal)
    assertEquals("https", scheme)
    assertEquals("api.example.com", host)
    assertEquals(443, port)
    assertEquals(expectedEndpoint, endPoint)
    assertEquals(HttpMethod.GET, method)
  }
}
