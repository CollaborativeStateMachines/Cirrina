package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Extent
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals

class ServiceImplementationTest {

  companion object {
    private lateinit var httpServer: HttpServer

    @JvmStatic
    @BeforeAll
    fun setUp() {
      httpServer =
        HttpServer.create(InetSocketAddress(8000), 0).apply {
          createContext("/plus") { exchange ->
            val result =
              exchange.readVariables().let { vars ->
                val v1 = vars.first { it.name == "varOne" }.value as Int
                val v2 = vars.first { it.name == "varTwo" }.value as Int
                listOf(ContextVariable.eager("result", v1 + v2))
              }
            exchange.sendVariables(result)
          }

          createContext("/error") { it.sendResponseHeaders(500, -1) }

          createContext("/broken-response1") { exchange ->
            exchange.sendResponseHeaders(200, 1)
            exchange.responseBody.use { it.write(byteArrayOf(1)) }
          }

          start()
        }
    }

    @JvmStatic @AfterAll fun tearDown() = httpServer.stop(0)

    private fun HttpExchange.readVariables(): List<ContextVariable> =
      requestBody.use { stream -> Serializer.deserialize(stream.readAllBytes()) }

    private fun HttpExchange.sendVariables(variables: List<ContextVariable>) =
      Serializer.serialize(variables).let { bytes ->
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
      }
  }

  @Test
  fun testHttpServiceInvocation() = runBlocking {
    listOf(Csml.HttpMethod.POST, Csml.HttpMethod.GET).forEach { method ->
      val result = assertDoesNotThrow {
        createService("/plus", method)
          .invoke(
            listOf(
              ContextVariable.eager("varOne", 5).evaluate(Extent.empty()),
              ContextVariable.eager("varTwo", 6).evaluate(Extent.empty()),
            )
          )
      }

      assertEquals(1, result.size)
      assertEquals("result", result.first().name)
      assertEquals(11, result.first().value)

      assertThrows<IllegalStateException> { createService("/error", method).invoke(emptyList()) }

      createService("/broken-response1", method).invoke(emptyList())
    }
  }

  private fun createService(path: String, method: Csml.HttpMethod) =
    HttpServiceImplementation("http", "localhost", 8000, path, method, "test-service", false)
}
