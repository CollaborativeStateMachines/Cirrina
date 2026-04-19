package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml.HttpMethod
import at.ac.uibk.dps.cirrina.csm.Csml.HttpServiceImplementationBinding
import at.ac.uibk.dps.cirrina.csm.Csml.ServiceImplementationBinding
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import at.ac.uibk.dps.cirrina.spec.ContextVariable
import at.ac.uibk.dps.cirrina.spec.LazyContextVariable
import com.google.protobuf.InvalidProtocolBufferException
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import kotlin.collections.isEmpty
import kotlinx.coroutines.future.await

abstract class ServiceImplementation(val name: String, val isLocal: Boolean) {
  abstract suspend fun invoke(input: List<ContextVariable>): List<ContextVariable>

  companion object {
    fun from(binding: ServiceImplementationBinding) =
      when (binding) {
        is HttpServiceImplementationBinding ->
          HttpServiceImplementation(
            binding.scheme,
            binding.host,
            binding.port.toInt(),
            binding.endPoint,
            binding.method,
            binding.name,
            binding.isLocal,
          )
        else -> error("unexpected service binding type: ${binding::class.simpleName}")
      }

    fun from(bindings: List<ServiceImplementationBinding>) =
      bindings.map { from(it) }.groupBy { it.name }
  }
}

class HttpServiceImplementation(
  private val scheme: String,
  private val host: String,
  private val port: Int,
  private val endPoint: String,
  private val method: HttpMethod,
  name: String,
  local: Boolean,
) : ServiceImplementation(name, local) {
  private val httpClient: HttpClient =
    HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build()

  override suspend fun invoke(input: List<ContextVariable>): List<ContextVariable> {
    require(input.none { it is LazyContextVariable }) {
      "all variables must be evaluated before conversion"
    }

    val payload = Serializer.serialize(input)
    val uri = URI(scheme, null, host, port, endPoint, null, null)

    val request =
      HttpRequest.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .header("Content-Type", "application/x-protobuf")
        .method(method.toString(), HttpRequest.BodyPublishers.ofByteArray(payload))
        .uri(uri)
        .build()

    val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()

    return handleResponse(response)
  }

  private fun handleResponse(response: HttpResponse<ByteArray>): List<ContextVariable> {
    val statusCode = response.statusCode()

    if (statusCode != HttpURLConnection.HTTP_OK) {
      error("http error ($statusCode)")
    }

    val body = response.body()
    if (body == null || body.isEmpty()) return emptyList()

    return try {
      Serializer.deserialize(body)
    } catch (_: InvalidProtocolBufferException) {
      error("unexpected http service response format")
    }
  }
}
