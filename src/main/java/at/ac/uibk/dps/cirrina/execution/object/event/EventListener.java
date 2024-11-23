package at.ac.uibk.dps.cirrina.execution.object.event;

import io.opentelemetry.api.trace.Span;

public interface EventListener {

  boolean onReceiveEvent(Event event, Span parentSpan);
}
