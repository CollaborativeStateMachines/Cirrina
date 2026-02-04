package at.ac.uibk.dps.cirrina.execution.service

import at.ac.uibk.dps.cirrina.csm.Csml.HttpMethod
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableExchange
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import com.google.protobuf.InvalidProtocolBufferException
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import kotlinx.coroutines.future.await

class HttpServiceImplementation(
  private val scheme: String,
  private val host: String,
  private val port: Int,
  private val endPoint: String,
  private val method: HttpMethod,
  name: String,
  local: Boolean,
) : ServiceImplementation(name, local) {

  private val virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()

  private val httpClient: HttpClient = HttpClient.newBuilder().executor(virtualExecutor).build()

  override suspend fun invoke(input: List<ContextVariable>): List<ContextVariable> =
    input
      .apply {
        require(none { it.isLazy }) {
          "all variables need to be evaluated before service input can be converted to bytes"
        }
      }
      .let { vars -> serializeInput(vars) }
      .let { payload ->
        HttpRequest.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .header("Content-Type", "application/x-protobuf")
          .method(method.toString(), HttpRequest.BodyPublishers.ofByteArray(payload))
          .uri(URI(scheme, null, host, port, endPoint, null, null))
          .build()
      }
      .let { request ->
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
      }
      .let { response -> handleResponse(response) }

  private fun serializeInput(input: List<ContextVariable>): ByteArray =
    input
      .takeIf { it.isNotEmpty() }
      ?.let { vars ->
        ContextVariableProtos.ContextVariables.newBuilder()
          .addAllData(vars.map { ContextVariableExchange(it).toProto() })
          .build()
          .toByteArray()
      } ?: byteArrayOf()

  private fun handleResponse(response: HttpResponse<ByteArray>): List<ContextVariable> =
    response
      .takeIf { it.statusCode() == HttpURLConnection.HTTP_OK }
      ?.body()
      ?.takeIf { it.isNotEmpty() }
      ?.let { payload ->
        try {
          ContextVariableProtos.ContextVariables.parseFrom(payload).dataList.map {
            ContextVariableExchange.fromProto(it)
          }
        } catch (_: InvalidProtocolBufferException) {
          error("unexpected http service invocation value type")
        }
      }
      ?: if (response.statusCode() != HttpURLConnection.HTTP_OK) {
        error("http error (${response.statusCode()})")
      } else {
        emptyList()
      }
}
