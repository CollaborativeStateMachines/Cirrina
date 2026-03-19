package at.ac.uibk.dps.cirrina.util

import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Paths

object TestUtils {
  fun resourceUri(path: String): URI {
    val url =
      TestUtils::class.java.classLoader.getResource(path) ?: error("resource not found '$path'")
    return Paths.get(url.toURI()).toUri()
  }

  fun mockHttpServer(handlerBlock: (List<ContextVariable>) -> List<ContextVariable>): HttpServer {
    val httpServer = HttpServer.create(InetSocketAddress(8000), 0)

    httpServer.createContext("/increment") { exchange ->
      val input: List<ContextVariable> = Serializer.deserialize(exchange.requestBody.readAllBytes())

      val output = handlerBlock(input)

      val out = Serializer.serialize(output)

      exchange.sendResponseHeaders(200, out.size.toLong())
      exchange.responseBody.use { stream -> stream.write(out) }
    }

    httpServer.start()

    return httpServer
  }
}
