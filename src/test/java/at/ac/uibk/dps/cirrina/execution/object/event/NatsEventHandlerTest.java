package at.ac.uibk.dps.cirrina.execution.object.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.ContextVariableDescription;
import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.EventChannel;
import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.EventDescription;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.context.InMemoryContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class NatsEventHandlerTest {

  @Test
  void testNatsEventHandlerSendReceiveGlobal() throws Exception {
    String natsServerURL = System.getenv("NATS_SERVER_URL");

    Assumptions.assumeFalse(natsServerURL == null, "Skipping NATS event handler test");

    var latch = new CountDownLatch(5);

    var eventListener = new EventListener() {
      public final List<Event> events = new ArrayList<>();

      @Override
      public boolean onReceiveEvent(Event event) {
        events.add(event);

        latch.countDown();

        return true;
      }
    };

    var localContext = new InMemoryContext(true);

    var natsEventHandler = new NatsEventHandler(natsServerURL);

    var expressionClass = "5";

    var contextVariableClass = new ContextVariableDescription("varName", expressionClass);

    var eventClass = new EventDescription("e1", EventChannel.GLOBAL, List.of(contextVariableClass));

    var e1 = EventBuilder.from(eventClass).build();

    natsEventHandler.addListener(eventListener);
    natsEventHandler.subscribe("e1");

    assertDoesNotThrow(() -> {
      for (int i = 0; i < 5; ++i) {
        natsEventHandler.sendEvent(
          Event.ensureHasEvaluatedData(e1, new Extent(localContext)),
          "source"
        );
      }
    });

    latch.await();

    assertEquals(5, eventListener.events.size());

    for (var e : eventListener.events) {
      assertEquals("e1", e.getName());
      assertEquals(EventChannel.GLOBAL, e.getChannel());
      assertEquals(1, e.getData().size());

      var ed = e.getData().getFirst();

      assertEquals("varName", ed.name());
      assertEquals(5, ed.value());
      assertFalse(ed.isLazy());
    }

    eventListener.events.clear();

    natsEventHandler.unsubscribe("e1");

    assertDoesNotThrow(() -> {
      for (int i = 0; i < 5; ++i) {
        natsEventHandler.sendEvent(
          Event.ensureHasEvaluatedData(e1, new Extent(localContext)),
          "source"
        );
      }
    });

    assertEquals(0, eventListener.events.size());

    natsEventHandler.unsubscribe("e1");

    natsEventHandler.close();
  }

  @Test
  void testNatsEventHandlerSendReceiveExternal() throws Exception {
    String natsServerURL = System.getenv("NATS_SERVER_URL");

    Assumptions.assumeFalse(natsServerURL == null, "Skipping NATS event handler test");

    var latch = new CountDownLatch(5);

    var eventListener = new EventListener() {
      public final List<Event> events = new ArrayList<>();

      @Override
      public boolean onReceiveEvent(Event event) {
        events.add(event);

        latch.countDown();

        return true;
      }
    };

    var localContext = new InMemoryContext(true);

    var natsEventHandler = new NatsEventHandler(natsServerURL);

    var expressionClass = "5";

    var contextVariableClass = new ContextVariableDescription("varName", expressionClass);

    var eventClass = new EventDescription(
      "e1",
      EventChannel.EXTERNAL,
      List.of(contextVariableClass)
    );

    var e1 = EventBuilder.from(eventClass).build();

    natsEventHandler.addListener(eventListener);
    natsEventHandler.subscribe("source", "e1");

    assertDoesNotThrow(() -> {
      for (int i = 0; i < 5; ++i) {
        natsEventHandler.sendEvent(
          Event.ensureHasEvaluatedData(e1, new Extent(localContext)),
          "source"
        );
      }
    });

    latch.await();

    assertEquals(5, eventListener.events.size());

    for (var e : eventListener.events) {
      assertEquals("e1", e.getName());
      assertEquals(EventChannel.EXTERNAL, e.getChannel());
      assertEquals(1, e.getData().size());

      var ed = e.getData().getFirst();

      assertEquals("varName", ed.name());
      assertEquals(5, ed.value());
      assertFalse(ed.isLazy());
    }

    eventListener.events.clear();

    natsEventHandler.unsubscribe("source", "e1");

    assertDoesNotThrow(() -> {
      for (int i = 0; i < 5; ++i) {
        natsEventHandler.sendEvent(
          Event.ensureHasEvaluatedData(e1, new Extent(localContext)),
          "source"
        );
      }
    });

    assertEquals(0, eventListener.events.size());

    natsEventHandler.unsubscribe("source", "e1");

    natsEventHandler.close();
  }
}
