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

class HttpServiceImplementation(parameters: Parameters) :
  ServiceImplementation(parameters.name, parameters.local) {

  private val httpClient: HttpClient =
    HttpClient.newBuilder().executor(Executors.newCachedThreadPool()).build()

  private val scheme = parameters.scheme
  private val host = parameters.host
  private val port = parameters.port.toInt()
  private val endPoint = parameters.endPoint
  private val method = parameters.method

  override suspend fun invoke(
    input: List<ContextVariable>,
    id: String,
  ): Result<List<ContextVariable>> = runCatching {
    require(input.none { it.isLazy }) {
      "All variables must be evaluated before service invocation"
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
        .header("Cirrina-Sender-ID", id)
        .method(method.toString(), HttpRequest.BodyPublishers.ofByteArray(payload))
        .uri(uri)
        .build()

    val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()

    handleResponse(response).getOrThrow()
  }

  private fun handleResponse(response: HttpResponse<ByteArray>): Result<List<ContextVariable>> =
    runCatching {
      if (response.statusCode() != HttpURLConnection.HTTP_OK) {
        error("HTTP error (${response.statusCode()})")
      }

      val payload = response.body()
      if (payload.isEmpty()) return Result.success(emptyList())

      ContextVariableProtos.ContextVariables.parseFrom(payload)
        .dataList
        .map { ContextVariableExchange.fromProto(it) }
        .map { it.getOrThrow() }
    }

  override val informationString: String
    get() =
      runCatching { URI(scheme, null, host, port, endPoint, null, null).toString() }
        .getOrElse { "Invalid URI: ${it.message}" }

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
