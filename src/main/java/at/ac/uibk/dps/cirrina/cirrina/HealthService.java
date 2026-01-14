package at.ac.uibk.dps.cirrina.cirrina;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Health service (HTTP) available at a specified port.
 * <p>
 * The health endpoint is "/".
 */
public class HealthService implements AutoCloseable {

  private final HttpServer httpServer;

  /**
   * Initializes this HealthService object.
   * <p>
   * Starts the service in a background thread.
   *
   * @param port Health service port.
   */
  public HealthService(int port) {
    try {
      httpServer = HttpServer.create(new InetSocketAddress(port), 0);

      httpServer.createContext("/", exchange -> {
        final var out = "OK".getBytes(StandardCharsets.UTF_8);

        // Response stateMachineInstanceStatus and length
        exchange.sendResponseHeaders(200, out.length);

        // Output the response
        try (final var stream = exchange.getResponseBody()) {
          stream.write(out);
        }
      });

      httpServer.start();
    } catch (IOException e) {
      throw new RuntimeException("failed to start health service: " + e);
    }
  }

  /**
   * Stops the health service.
   */
  @Override
  public void close() {
    httpServer.stop(0);
  }
}
