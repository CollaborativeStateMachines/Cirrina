package at.ac.uibk.dps.cirrina.observability;

import io.opentelemetry.context.Context;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;

public class Tracing {

//  public Tracer initializeTracer(String name){
//    return GlobalOpenTelemetry.getTracer(name);
//  }

  public Span initializeSpan(String name, Tracer tracer, Span parentSpan, Map<String, String> attributes){
    Span span;
    if(parentSpan == null) {
      span = tracer.spanBuilder(name).startSpan();
    } else {
      span = tracer.spanBuilder(name).setParent(Context.current().with(parentSpan)).startSpan();
    }
    for (Map.Entry<String, String> entry : attributes.entrySet()){
      span.setAttribute(entry.getKey(), entry.getValue());
    }
    return span;
  }

  public void recordException(Throwable throwable, Span span){
    span.recordException(throwable);
    span.setStatus(StatusCode.ERROR, throwable.getMessage());
    span.setAttribute("Message", throwable.getMessage());
  }

//  public void addEvent(String event, Span span){
//    span.addEvent(event);
//  }


}
