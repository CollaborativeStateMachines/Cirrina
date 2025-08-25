package at.ac.uibk.dps.cirrina.observability.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.Map;

public class TracingHelper {

  public Span initializeSpanWithParent(String name, Tracer tracer, Context parent, SpanKind kind) {
    Span span = tracer.spanBuilder(name).setParent(parent).setSpanKind(kind).startSpan();
    return span;
  }

  public void recordException(Throwable throwable, Span span) {
    if (span == null || throwable == null) {
      return;
    }
    span.recordException(throwable);
    span.setStatus(StatusCode.ERROR, String.valueOf(throwable.getMessage()));
    span.setAttribute("exception.message", String.valueOf(throwable.getMessage()));
    span.setAttribute("exception.type", throwable.getClass().getName());
  }

  public Span initializeSpanWithoutParent(String name, Tracer tracer, SpanKind kind, Map<String, String> attributes) {
    Span span = tracer.spanBuilder(name).setSpanKind(kind).startSpan();
    if (attributes != null) {
      for (Map.Entry<String, String> e : attributes.entrySet()) {
        if (e.getKey() != null && e.getValue() != null) {
          span.setAttribute(e.getKey(), e.getValue());
        }
      }
    }
    return span;
  }
}
