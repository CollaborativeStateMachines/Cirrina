package at.ac.uibk.dps.cirrina.runtime.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass;
import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClassBuilder;
import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription;
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions;
import at.ac.uibk.dps.cirrina.execution.object.context.InMemoryContext;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.event.EventHandler;
import at.ac.uibk.dps.cirrina.execution.service.OptimalServiceImplementationSelector;
import at.ac.uibk.dps.cirrina.io.description.DescriptionParser;
import at.ac.uibk.dps.cirrina.runtime.OfflineRuntime;
import com.google.common.collect.ArrayListMultimap;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PingPongTest {

  private static CollaborativeStateMachineClass collaborativeStateMachineClass;

  @BeforeAll
  public static void setUp() {
    var json = DefaultDescriptions.pingPong;

    var parser = new DescriptionParser<CollaborativeStateMachineDescription>(
      CollaborativeStateMachineDescription.class
    );
    Assertions.assertDoesNotThrow(() -> {
      collaborativeStateMachineClass = CollaborativeStateMachineClassBuilder.from(
        parser.parse(json)
      ).build();
    });
  }

  private static InMemoryContext getMockPersistentContext() throws IOException {
    final var mockPersistentContext = new InMemoryContext(true) {
      private int next = 1;

      @Override
      public int assign(String name, Object value) throws IOException {
        // Don't expect any variables assigned except for v
        assertEquals("v", name);

        // Which is an integer
        assertInstanceOf(Integer.class, value);

        // And should count up to 100
        assertEquals(next++, value);
        assertTrue((Integer) value <= 100);

        return super.assign(name, value);
      }
    };

    mockPersistentContext.create("v", 0);
    return mockPersistentContext;
  }

  @Test
  void testPingPongExecute() {
    Assertions.assertDoesNotThrow(() -> {
      final var mockEventHandler = new EventHandler() {
        @Override
        public void close() throws Exception {}

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

      final var mockPersistentContext = getMockPersistentContext();

      final var runtime = new OfflineRuntime("runtime", mockEventHandler, mockPersistentContext);
      final var serviceImplementationSelector = new OptimalServiceImplementationSelector(
        ArrayListMultimap.create()
      );

      final var instances = runtime.newInstance(
        collaborativeStateMachineClass,
        serviceImplementationSelector
      );

      assertEquals(2, instances.size());

      final var instance = runtime.findInstance(instances.getFirst()).get();

      assertTrue(runtime.waitForCompletion(10000));

      assertEquals(100, mockPersistentContext.get("v"));
    });
  }
}
