package at.ac.uibk.dps.cirrina.util

import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import at.ac.uibk.dps.cirrina.execution.provider.InMemoryContext
import at.ac.uibk.dps.cirrina.execution.util.ContextVariableExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Paths

object TestUtils {
  fun resourceUri(path: String): URI {
    val url =
      TestUtils::class.java.classLoader.getResource(path)
        ?: throw IllegalArgumentException("resource not found: $path")
    return Paths.get(url.toURI()).toUri()
  }

  fun mockPersistentContext(
    createBlock: InMemoryContext.() -> Unit = {},
    assignBlock: (superAssign: (String, Any?) -> Int, name: String, value: Any?) -> Int =
      { superAssign, name, value ->
        superAssign(name, value)
      },
  ): InMemoryContext {
    val mockPersistentContext =
      object : InMemoryContext() {
        override fun assign(name: String, value: Any?): Int {
          return assignBlock({ n, v -> super.assign(n, v) }, name, value)
        }
      }
    mockPersistentContext.createBlock()
    return mockPersistentContext
  }

  fun mockHttpServer(handlerBlock: (List<ContextVariable>) -> List<ContextVariable>): HttpServer {
    val httpServer = HttpServer.create(InetSocketAddress(8000), 0)

    httpServer.createContext("/increment") { exchange ->
      val payload = exchange.requestBody.readAllBytes()

      val input =
        ContextVariableProtos.ContextVariables.parseFrom(payload).dataList.map {
          ContextVariableExchange.fromProto(it)
        }

      val output = handlerBlock(input)

      val out =
        ContextVariableProtos.ContextVariables.newBuilder()
          .addAllData(output.map { ContextVariableExchange(it).toProto() })
          .build()
          .toByteArray()

      exchange.sendResponseHeaders(200, out.size.toLong())
      exchange.responseBody.use { stream -> stream.write(out) }
    }

    httpServer.start()

    return httpServer
  }
}
