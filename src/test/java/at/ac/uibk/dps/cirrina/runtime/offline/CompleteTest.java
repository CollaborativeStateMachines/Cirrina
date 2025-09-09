package at.ac.uibk.dps.cirrina.runtime.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass;
import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.csm.description.HttpServiceImplementationDescription;
import at.ac.uibk.dps.cirrina.csm.description.HttpServiceImplementationDescription.Method;
import at.ac.uibk.dps.cirrina.csm.description.ServiceImplementationDescription;
import at.ac.uibk.dps.cirrina.csm.description.ServiceImplementationDescription.Type;
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.context.InMemoryContext;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.execution.object.exchange.ContextVariableExchange;
import at.ac.uibk.dps.cirrina.execution.object.exchange.ContextVariableProtos;
import at.ac.uibk.dps.cirrina.execution.service.OptimalServiceImplementationSelector;
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder;
import at.ac.uibk.dps.cirrina.io.description.CsmlParser;
import at.ac.uibk.dps.cirrina.runtime.OfflineRuntime;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CompleteTest {

  private static CollaborativeStateMachineClass collaborativeStateMachineClass;

  private static HttpServer httpServer;

  @BeforeAll
  public static void setUp() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(8000), 0);

    httpServer.createContext("/increment", exchange -> {
      final var payload = exchange.getRequestBody().readAllBytes();

      final var in = ContextVariableProtos.ContextVariables.parseFrom(payload)
        .getDataList()
        .stream()
        .map(ContextVariableExchange::fromProto)
        .toList();

      final var v = in
        .stream()
        .filter(e -> e.name().equals("v"))
        .findFirst();

      // Create output
      final var out = ContextVariableProtos.ContextVariables.newBuilder()
        .addAllData(
          Stream.of(new ContextVariable("v", (int) v.get().value() + 1))
            .map(contextVariable -> new ContextVariableExchange(contextVariable).toProto())
            .toList()
        )
        .build()
        .toByteArray();

      // Response stateMachineInstanceStatus and length
      exchange.sendResponseHeaders(200, out.length);

      // Output the response
      try (final var stream = exchange.getResponseBody()) {
        stream.write(out);
      }
    });

    httpServer.start();

    Assertions.assertDoesNotThrow(() -> {
      collaborativeStateMachineClass = CollaborativeStateMachineClassBuilder.from(
        CsmlParser.parse(DefaultDescriptions.complete)
      ).build();
    });
  }

  @AfterAll
  public static void tearDown() {
    httpServer.stop(0);
  }

  @Test
  void testServiceInvocationExecute() {
    Assertions.assertDoesNotThrow(() -> {
      final var mockEventHandler = new EventHandler() {
        @Override
        public void close() {}

        @Override
        public void sendEvent(Event event, String source) {
          propagateEvent(event);
        }

        @Override
        public void subscribe(String topic) {}

        @Override
        public void unsubscribe(String topic) {}

        @Override
        public void subscribe(String source, String subject) {}

        @Override
        public void unsubscribe(String source, String subject) {}
      };

      // Mock a persistent context using an in-memory context
      var mockPersistentContext = new InMemoryContext(true) {
        @Override
        public int assign(String name, Object value) throws IOException {
          System.out.println("Assigning " + name + " with value " + value);

          // Don't expect any variables assigned except for v
          assertTrue(name.equals("v"));

          // Which is an integer
          assertInstanceOf(Integer.class, value);

          return super.assign(name, value);
        }
      };

      final var runtime = new OfflineRuntime("runtime", mockEventHandler, mockPersistentContext);

      var serviceDescriptions = new ServiceImplementationDescription[1];

      {
        var service = new HttpServiceImplementationDescription(
          "increment",
          1.0,
          true,
          Type.HTTP,
          "http",
          "localhost",
          8000,
          "/increment",
          Method.GET
        );

        serviceDescriptions[0] = service;
      }

      final var services = ServiceImplementationBuilder.from(List.of(serviceDescriptions)).build();
      final var serviceImplementationSelector = new OptimalServiceImplementationSelector(services);

      final var instances = runtime.newInstance(
        collaborativeStateMachineClass,
        serviceImplementationSelector
      );

      assertEquals(1, instances.size());

      assertTrue(runtime.waitForCompletion(10000));

      assertEquals(0, mockPersistentContext.get("v"));
      assertEquals(true, mockPersistentContext.get("b"));
    });
  }
}
