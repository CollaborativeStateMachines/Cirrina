package at.ac.uibk.dps.cirrina.cirrina

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Provides a lightweight HTTP health check service.
 *
 * This service exposes a single endpoint at the root ("/") which returns a "200 OK" response,
 * allowing external monitors to verify that the application process is alive.
 *
 * @property port the network port on which the health service listens.
 */
class HealthService(port: Int) : AutoCloseable {

  private val httpServer: HttpServer =
    try {
      HttpServer.create(InetSocketAddress(port), 0).apply {
        createContext("/", ::handleHealthCheck)
        start()
      }
    } catch (_: IOException) {
      error("Failed to start health service on port $port")
    }

  private fun handleHealthCheck(exchange: HttpExchange) =
    "OK".toByteArray(StandardCharsets.UTF_8).let { response ->
      exchange.sendResponseHeaders(200, response.size.toLong())
      exchange.responseBody.use { it.write(response) }
    }

  /** Gracefully stops the health service. */
  override fun close() = httpServer.stop(0)
}
