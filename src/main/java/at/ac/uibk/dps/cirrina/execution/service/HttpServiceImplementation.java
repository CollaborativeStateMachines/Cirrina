package at.ac.uibk.dps.cirrina.execution.service;

import at.ac.uibk.dps.cirrina.csml.description.HttpServiceImplementationDescription.Method;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.exchange.ContextVariableExchange;
import at.ac.uibk.dps.cirrina.execution.object.exchange.ContextVariableProtos;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * HTTP service implementation, a service implementation that is accessible through HTTP.
 * <p>
 * Input variables provided to the invoked service are provided as a list of context variables.
 * <p>
 * Context variables are encoded using Protocol Buffers.
 */
public class HttpServiceImplementation extends ServiceImplementation {

  /**
   * HTTP client.
   */
  private final HttpClient httpClient = HttpClient.newBuilder()
    .executor(Executors.newCachedThreadPool())
    .build();

  /**
   * Handle executor.
   */
  private final Executor handleExecutor = Executors.newCachedThreadPool();

  /**
   * HTTP scheme.
   */
  private final String scheme;

  /**
   * HTTP host.
   */
  private final String host;

  /**
   * HTTP port.
   */
  private final int port;

  /**
   * HTTP end-point,
   */
  private final String endPoint;

  /**
   * HTTP method.
   */
  private final Method method;

  /**
   * Initializes this HTTP service implementation.
   *
   * @param parameters Initialization parameters.
   */
  public HttpServiceImplementation(Parameters parameters) {
    super(parameters.name, parameters.cost, parameters.local);
    this.scheme = parameters.scheme;
    this.host = parameters.host;
    this.port = (int) parameters.port;
    this.endPoint = parameters.endPoint;
    this.method = parameters.method;
  }

  /**
   * Handle a service invocation response.
   *
   * @param response HTTP response.
   * @return Output variables.
   * @throws CompletionException In case of error.
   */
  private static List<ContextVariable> handleResponse(HttpResponse<byte[]> response) {
    // Require HTTP OK
    final var errorCode = response.statusCode();

    if (errorCode != HttpURLConnection.HTTP_OK) {
      throw new CompletionException(new IOException("HTTP error (%d)".formatted(errorCode)));
    }

    // Acquire the payload
    final var payload = response.body();

    try {
      // Empty payload
      if (payload.length == 0) {
        return List.of();
      }

      // Otherwise we expect a serialized collection of context variables
      return ContextVariableProtos.ContextVariables.parseFrom(payload)
        .getDataList()
        .stream()
        .map(ContextVariableExchange::fromProto)
        .toList();
    } catch (InvalidProtocolBufferException e) {
      throw new CompletionException(
        new IOException("Unexpected HTTP service invocation value type")
      );
    }
  }

  /**
   * Invoke this service implementation.
   * <p>
   * All input variables must be evaluated.
   *
   * @param input Input to the service invocation.
   * @param id    Sender ID.
   * @return The service invocation output.
   * @throws UnsupportedOperationException If not all variables are evaluated.
   * @throws UnsupportedOperationException If the invocation failed.
   */
  @Override
  public CompletableFuture<List<ContextVariable>> invoke(List<ContextVariable> input, String id)
    throws UnsupportedOperationException {
    try {
      if (input.stream().anyMatch(ContextVariable::isLazy)) {
        throw new UnsupportedOperationException(
          "All variables need to be evaluated before service input can be converted to bytes"
        );
      }

      // Serialize the data
      final byte[] payload = input.isEmpty()
        ? new byte[0]
        : ContextVariableProtos.ContextVariables.newBuilder()
          .addAllData(
            input
              .stream()
              .map(contextVariable -> new ContextVariableExchange(contextVariable).toProto())
              .toList()
          )
          .build()
          .toByteArray();

      // Create URL
      final var uri = new URI(scheme, null, host, port, endPoint, null, null);

      final var request = HttpRequest.newBuilder()
        .version(Version.HTTP_1_1)
        .header("Cirrina-Sender-ID", id)
        .method(method.toString(), BodyPublishers.ofByteArray(payload))
        .uri(uri)
        .build();

      return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .thenApplyAsync(HttpServiceImplementation::handleResponse, handleExecutor);
    } catch (URISyntaxException | UnsupportedOperationException e) {
      throw new UnsupportedOperationException("Failed to perform HTTP service invocation", e);
    }
  }

  /**
   * Returns the dynamic performance of this service implementation.
   *
   * @return Performance.
   */
  @Override
  public float getPerformance() {
    return 1.0f; // TODO: Implement measuring of performance
  }

  /**
   * Returns a string for informative purposes (a service implementation is abstract, so can provide no information about the details).
   *
   * @return Information string.
   */
  @Override
  public String getInformationString() {
    try {
      final var uri = new URI(scheme, null, host, port, endPoint, null, null);

      return "%s".formatted(uri.toString());
    } catch (URISyntaxException e) {
      return "Invalid information string: %s".formatted(e.getMessage());
    }
  }

  public record Parameters(
    String name,
    double cost,
    boolean local,
    String scheme,
    String host,
    long port,
    String endPoint,
    Method method
  ) {}
}
