package at.ac.uibk.dps.cirrina.execution.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import at.ac.uibk.dps.cirrina.csm.Csml.ContextVariableDescription;
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings.HttpMethod;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariableBuilder;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.exchange.ContextVariableExchange;
import at.ac.uibk.dps.cirrina.execution.object.exchange.ContextVariableProtos;
import at.ac.uibk.dps.cirrina.execution.service.HttpServiceImplementation.Parameters;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class HttpServiceImplementationTest {

  private static HttpServer httpServer;

  @BeforeAll
  public static void setUp() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(8000), 0);

    httpServer.createContext("/plus", exchange -> {
      Assertions.assertEquals(
        "some-id",
        exchange.getRequestHeaders().get("Cirrina-Sender-ID").getFirst()
      );

      final var payload = exchange.getRequestBody().readAllBytes();

      final var in = ContextVariableProtos.ContextVariables.parseFrom(payload)
        .getDataList()
        .stream()
        .map(ContextVariableExchange::fromProto)
        .toList();

      final var varOne = in
        .stream()
        .filter(e -> e.name().equals("varOne"))
        .findFirst();
      final var varTwo = in
        .stream()
        .filter(e -> e.name().equals("varTwo"))
        .findFirst();

      // Create output
      final var out = ContextVariableProtos.ContextVariables.newBuilder()
        .addAllData(
          Stream.of(
            new ContextVariable("result", (int) varOne.get().value() + (int) varTwo.get().value())
          )
            .map(contextVariable -> new ContextVariableExchange(contextVariable).toProto())
            .toList()
        )
        .build()
        .toByteArray();

      // Response status and length
      exchange.sendResponseHeaders(200, out.length);

      // Output the response
      try (final var stream = exchange.getResponseBody()) {
        stream.write(out);
      }
    });

    httpServer.createContext("/error", exchange -> {
      // Response status and length
      exchange.sendResponseHeaders(500, 0);

      // Output the response
      try (final var stream = exchange.getResponseBody()) {
        stream.write(new byte[0]);
      }
    });

    httpServer.createContext("/broken-response1", exchange -> {
      // Response status and length
      exchange.sendResponseHeaders(200, 1);

      // Output the response
      try (final var stream = exchange.getResponseBody()) {
        stream.write(new byte[] { 1 });
      }
    });

    httpServer.createContext("/broken-response2", exchange -> {
      final byte[] out = null;

      // Response status and length
      exchange.sendResponseHeaders(200, out.length);

      // Output the response
      try (final var stream = exchange.getResponseBody()) {
        stream.write(out);
      }
    });

    httpServer.start();
  }

  @AfterAll
  public static void tearDown() {
    httpServer.stop(0);
  }

  @Test
  public void testHttpServiceInvocation() {
    List.of(HttpMethod.POST, HttpMethod.GET)
      .stream()
      .forEach(method -> {
        // First variable
        final var varOne = new ContextVariableDescription("varOne", "5");

        // Second variable
        final var varTwo = new ContextVariableDescription("varTwo", "6");

        // Success
        assertDoesNotThrow(() -> {
          final var variables = new ArrayList<ContextVariable>();
          variables.add(ContextVariableBuilder.from(varOne).build().evaluate(new Extent()));
          variables.add(ContextVariableBuilder.from(varTwo).build().evaluate(new Extent()));

          final var service = new HttpServiceImplementation(
            new Parameters("http", false, "http", "localhost", 8000, "/plus", HttpMethod.POST)
          );

          final var output = service.invoke(variables, "some-id").get();

          assertEquals(1, output.size());

          final var result = output.getFirst();
          assertEquals("result", result.name());
          assertEquals(11, result.value());
        });

        // HTTP error
        assertThrows(ExecutionException.class, () -> {
          final var service = new HttpServiceImplementation(
            new Parameters("http", false, "http", "localhost", 8000, "/error", HttpMethod.POST)
          );

          service.invoke(new ArrayList<>(), "some-id").get();
        });

        // Invalid response type
        assertThrows(ExecutionException.class, () -> {
          final var service = new HttpServiceImplementation(
            new Parameters(
              "http",
              false,
              "http",
              "localhost",
              8000,
              "/broken-response1",
              HttpMethod.POST
            )
          );

          service.invoke(new ArrayList<>(), "some-id").get();
        });

        // Invalid response type
        assertThrows(ExecutionException.class, () -> {
          final var service = new HttpServiceImplementation(
            new Parameters(
              "http",
              false,
              "http",
              "localhost",
              8000,
              "/broken-response2",
              HttpMethod.POST
            )
          );

          service.invoke(new ArrayList<>(), "some-id").get();
        });
      });
  }
}
