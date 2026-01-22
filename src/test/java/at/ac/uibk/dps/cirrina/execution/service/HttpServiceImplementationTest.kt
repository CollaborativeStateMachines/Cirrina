package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.HttpMethod
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariableBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableExchange
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals

class HttpServiceImplementationTest {

  companion object {
    private lateinit var httpServer: HttpServer

    @JvmStatic
    @BeforeAll
    fun setUp() {
      httpServer =
        HttpServer.create(InetSocketAddress(8000), 0).apply {
          createContext("/plus") { exchange ->
            exchange
              .readVariables()
              .let { vars ->
                val v1 = vars.first { it.name == "varOne" }.value as Int
                val v2 = vars.first { it.name == "varTwo" }.value as Int
                listOf(ContextVariable("result", v1 + v2))
              }
              .let { result -> exchange.sendVariables(result) }
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
      requestBody.use { stream ->
        ContextVariableProtos.ContextVariables.parseFrom(stream.readAllBytes())
          .dataList
          .map(ContextVariableExchange::fromProto)
      }

    private fun HttpExchange.sendVariables(variables: List<ContextVariable>) =
      variables
        .map { ContextVariableExchange(it).toProto() }
        .let { protos ->
          ContextVariableProtos.ContextVariables.newBuilder()
            .addAllData(protos)
            .build()
            .toByteArray()
        }
        .let { bytes ->
          sendResponseHeaders(200, bytes.size.toLong())
          responseBody.use { it.write(bytes) }
        }
  }

  @Test
  fun testHttpServiceInvocation() = runBlocking {
    listOf(HttpMethod.POST, HttpMethod.GET).forEach { method ->
      // Success case
      assertDoesNotThrow {
          createService("/plus", method)
            .invoke(
              listOf(
                ContextVariableBuilder.empty()
                  .name("varOne")
                  .value(5)
                  .build()
                  .getOrThrow()
                  .evaluate(Extent.empty()),
                ContextVariableBuilder.empty()
                  .name("varTwo")
                  .value(6)
                  .build()
                  .getOrThrow()
                  .evaluate(Extent.empty()),
              )
            )
        }
        .let { result ->
          assertEquals(1, result.size)
          assertEquals("result", result.first().name)
          assertEquals(11, result.first().value)
        }

      // Error case
      assertThrows<IllegalStateException> { createService("/error", method).invoke(emptyList()) }

      // Broken serialization case
      assertThrows<IllegalStateException> {
        createService("/broken-response1", method).invoke(emptyList())
      }
    }
  }

  private fun createService(path: String, method: HttpMethod) =
    HttpServiceImplementation("http", "localhost", 8000, path, method, "test-service", false)
}
