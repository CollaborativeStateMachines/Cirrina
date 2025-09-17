package at.ac.uibk.dps.cirrina.execution.object.exchange;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventExchangeTest {

  @Test
  void testToFromBytes() {
    var contextVariable = new ContextVariable("varName", "some string");

    assertDoesNotThrow(() -> {
      var eventOut = new Event("name", EventChannel.EXTERNAL, List.of(contextVariable));
      var data = new EventExchange(eventOut).toBytes();

      var eventIn = EventExchange.fromBytes(data).getEvent();

      assertEquals(eventOut.getId(), eventIn.getId());
      assertEquals("name", eventIn.getName());
      assertEquals("EXTERNAL", eventIn.getChannel().name());
      assertEquals("varName", eventIn.getData().getFirst().name());
      assertEquals("some string", eventIn.getData().getFirst().value());
      assertFalse(eventIn.getData().getFirst().isLazy());
    });
  }
}
