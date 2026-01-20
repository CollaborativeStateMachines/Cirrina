package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableExchange
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import kotlinx.coroutines.future.await

/**
 * An HTTP-based implementation of a service.
 *
 * This implementation serializes context variables into a Protobuf payload, performs an
 * asynchronous HTTP request, and parses the resulting Protobuf response back into variables.
 *
 * @property parameters the configuration parameters for the HTTP connection.
 */
class HttpServiceImplementation(parameters: Parameters) :
  ServiceImplementation(parameters.name, parameters.local) {

  private val httpClient: HttpClient =
    HttpClient.newBuilder().executor(Executors.newCachedThreadPool()).build()

  private val scheme = parameters.scheme
  private val host = parameters.host
  private val port = parameters.port.toInt()
  private val endPoint = parameters.endPoint
  private val method = parameters.method

  /**
   * Invokes the HTTP service with the provided input.
   *
   * @param input the list of context variables to be sent as the request payload.
   * @return a [Result] containing the list of context variables returned by the service.
   */
  override suspend fun invoke(input: List<ContextVariable>): List<ContextVariable> {
    require(input.none { it.isLazy }) {
      "all variables must be evaluated before service invocation"
    }

    val payload =
      if (input.isEmpty()) {
        ByteArray(0)
      } else {
        ContextVariableProtos.ContextVariables.newBuilder()
          .addAllData(input.map { ContextVariableExchange(it).toProto().getOrThrow() })
          .build()
          .toByteArray()
      }

    val uri = URI(scheme, null, host, port, endPoint, null, null)
    val request =
      HttpRequest.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .method(method.toString(), HttpRequest.BodyPublishers.ofByteArray(payload))
        .uri(uri)
        .build()

    val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()

    return handleResponse(response)
  }

  private fun handleResponse(response: HttpResponse<ByteArray>): List<ContextVariable> {
    if (response.statusCode() != HttpURLConnection.HTTP_OK) {
      error("HTTP error (${response.statusCode()})")
    }

    val payload = response.body()

    if (payload.isEmpty()) return emptyList()

    return ContextVariableProtos.ContextVariables.parseFrom(payload).dataList.map { proto ->
      ContextVariableExchange.fromProto(proto).getOrThrow()
    }
  }

  /** Configuration parameters for an [HttpServiceImplementation]. */
  data class Parameters(
    val name: String,
    val local: Boolean,
    val scheme: String,
    val host: String,
    val port: Long,
    val endPoint: String,
    val method: ServiceImplementationBindings.HttpMethod,
  )
}
