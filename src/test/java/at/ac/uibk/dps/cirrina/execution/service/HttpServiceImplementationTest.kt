package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.HttpMethod
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariableBuilder
import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableExchange
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import at.ac.uibk.dps.cirrina.execution.service.HttpServiceImplementation.Parameters
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HttpServiceImplementationTest {

  companion object {
    private lateinit var httpServer: HttpServer

    @JvmStatic
    @BeforeAll
    fun setUp() {
      httpServer = HttpServer.create(InetSocketAddress(8000), 0)

      httpServer.createContext("/plus") { exchange ->
        assertEquals("some-id", exchange.requestHeaders["Cirrina-Sender-ID"]?.firstOrNull())

        val payload = exchange.requestBody.readAllBytes()
        val incoming =
          ContextVariableProtos.ContextVariables.parseFrom(payload).dataList.map {
            ContextVariableExchange.fromProto(it)
          }

        val varOne = incoming.first { it.name == "varOne" }
        val varTwo = incoming.first { it.name == "varTwo" }

        val out =
          ContextVariableProtos.ContextVariables.newBuilder()
            .addAllData(
              listOf(ContextVariable("result", (varOne.value as Int) + (varTwo.value as Int))).map {
                ContextVariableExchange(it).toProto()
              }
            )
            .build()
            .toByteArray()

        exchange.sendResponseHeaders(200, out.size.toLong())
        exchange.responseBody.use { it.write(out) }
      }

      httpServer.createContext("/error") { exchange ->
        exchange.sendResponseHeaders(500, 0)
        exchange.responseBody.close()
      }

      httpServer.createContext("/broken-response1") { exchange ->
        exchange.sendResponseHeaders(200, 1)
        exchange.responseBody.use { it.write(byteArrayOf(1)) }
      }

      httpServer.start()
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
      httpServer.stop(0)
    }
  }

  @Test
  fun testHttpServiceInvocation() = runBlocking {
    listOf(HttpMethod.POST, HttpMethod.GET).forEach { method ->

      // Success Case
      val variables =
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

      val service =
        HttpServiceImplementation(
          Parameters("http", false, "http", "localhost", 8000, "/plus", method)
        )

      val result = service.invoke(variables)

      assertEquals(1, result.size)
      val first = result.first()
      assertEquals("result", first.name)
      assertEquals(11, first.value)

      // HTTP Error Case (500)
      val errorService =
        HttpServiceImplementation(
          Parameters("http", false, "http", "localhost", 8000, "/error", method)
        )
      val errorResult = errorService.invoke(emptyList())

      // Invalid Protobuf Response
      val brokenService =
        HttpServiceImplementation(
          Parameters("http", false, "http", "localhost", 8000, "/broken-response1", method)
        )
      val brokenResult = brokenService.invoke(emptyList())
    }
  }
}
