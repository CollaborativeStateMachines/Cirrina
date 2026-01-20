package at.ac.uibk.dps.cirrina.utils

import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableExchange
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail

fun <T> Result<T>.assertValue(expected: T) {
  this.fold(
    onSuccess = { actual -> assertEquals(expected, actual) },
    onFailure = { ex ->
      fail("expected success with value [$expected], but failed with: ${ex.message}")
    },
  )
}

fun Result<*>.assertFailure() =
  assertTrue(this.isFailure, "expected Result.failure but was success")

fun Result<*>.assertSuccess() =
  assertTrue(this.isSuccess, "expected Result.success but was failure")

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
      object : InMemoryContext(true) {
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
          ContextVariableExchange.fromProto(it).getOrThrow()
        }

      val output = handlerBlock(input)

      val out =
        ContextVariableProtos.ContextVariables.newBuilder()
          .addAllData(output.map { ContextVariableExchange(it).toProto().getOrThrow() })
          .build()
          .toByteArray()

      exchange.sendResponseHeaders(200, out.size.toLong())
      exchange.responseBody.use { stream -> stream.write(out) }
    }

    httpServer.start()

    return httpServer
  }
}
