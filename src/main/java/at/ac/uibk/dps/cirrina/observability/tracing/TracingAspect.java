package at.ac.uibk.dps.cirrina.observability.tracing;

import static at.ac.uibk.dps.cirrina.observability.MethodName.*;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*;

import at.ac.uibk.dps.cirrina.observability.MethodName;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.lang.reflect.Method;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class TracingAspect {

  Tracer tracer = GlobalOpenTelemetry.getTracer("Cirrina");
  public static final TracingHelper TRACING_HELPER = new TracingHelper();

  private final ThreadLocal<Map<String, String>> stateMachineAttributes = ThreadLocal.withInitial(HashMap::new);
  private Span parentSpan;
  private final ThreadLocal<Set<String>> activeSpans = ThreadLocal.withInitial(HashSet::new);
  private final SpanHelper spanHelper = new SpanHelper();


  @Around("@annotation(at.ac.uibk.dps.cirrina.observability.tracing.Trace)")
  public Object tracingAspect(ProceedingJoinPoint joinPoint) throws Throwable {

    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Object target = joinPoint.getTarget();
    Object[] args = joinPoint.getArgs();
    Map<String, String> attributes = spanHelper.extractSpanAttributes(method, target, args, stateMachineAttributes.get());
    String spanName = "";

    //Adding the span name to the span. Custom for each span.
    if (attributes.containsKey("Name")) {
      spanName += attributes.remove("Name");
    }

    //Making sure that no duplicate spans are created
    if (activeSpans.get().contains(spanName)) {
      return joinPoint.proceed();
    }
    activeSpans.get().add(spanName);

    //Span initialization
    Span span = TRACING_HELPER.initializeSpanWithoutParent(spanName, tracer, attributes);

    //Manually setting the parent span for the "handleResponse" method. Must be done because
    // otherwise OTel does not recognize the parent-child relationship
    Trace traceAnnotation = method.getAnnotation(Trace.class);
    MethodName methodName = traceAnnotation.name();

    if (methodName.equals(INVOKE)) {
      parentSpan = span;
    }

    try (Scope scope = span.makeCurrent()) {
      return joinPoint.proceed();
    } catch (Throwable ex) {
      TRACING_HELPER.recordException(ex, span);
      throw ex;
    } finally {
      span.end();
      activeSpans.get().remove(spanName);
    }
  }

  //Applied only to handleResponse Method
  @Around("@annotation(at.ac.uibk.dps.cirrina.observability.tracing.TraceStatic)")
  public Object traceStaticHandleResponse(ProceedingJoinPoint joinPoint) throws Throwable {

    String spanName = "handleResponse";

    //Making sure that no duplicate spans are created
    if (activeSpans.get().contains(spanName)) {
      return joinPoint.proceed();
    }
    activeSpans.get().add(spanName);


    Span span = TRACING_HELPER.initializeSpanWithParent(spanName, tracer, parentSpan, stateMachineAttributes.get());

    try(Scope scope = span.makeCurrent()) {
      Object[] args = joinPoint.getArgs();
      if (args.length == 1 && args[0] instanceof HttpResponse response) {
        span.setAttribute(ATTR_RESPONSE, Arrays.toString((byte[]) response.body()));
        span.setAttribute(ATTR_STATE_MACHINE_ID, stateMachineAttributes.get().get(ATTR_STATE_MACHINE_ID));
        span.setAttribute(ATTR_STATE_MACHINE_NAME, stateMachineAttributes.get().get(ATTR_STATE_MACHINE_NAME));
        span.setAttribute(ATTR_PARENT_STATE_MACHINE_ID, stateMachineAttributes.get().get(ATTR_PARENT_STATE_MACHINE_ID));
        span.setAttribute(ATTR_PARENT_STATE_MACHINE_NAME, stateMachineAttributes.get().get(ATTR_PARENT_STATE_MACHINE_NAME));
      }

      return joinPoint.proceed();

    } catch (Throwable t) {
      span.recordException(t);
      throw t;
    } finally {
      span.end();
      activeSpans.get().remove(spanName);
    }
  }
}