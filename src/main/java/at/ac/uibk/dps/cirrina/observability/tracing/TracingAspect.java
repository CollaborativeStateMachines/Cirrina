package at.ac.uibk.dps.cirrina.observability.tracing;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_RESPONSE;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.ATTR_STATE_MACHINE_NAME;

import at.ac.uibk.dps.cirrina.observability.MethodName;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class TracingAspect {

  private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("Cirrina");
  private static final TracingHelper TRACING_HELPER = new TracingHelper();

  private static final ContextKey<Map<String, String>> STATE_MACHINE_CONTEXT =
      ContextKey.named("cirrina.sm.attrs");

  private final SpanHelper spanHelper = new SpanHelper();

  // Only used to filter immediate, accidental re-entrancy in the SAME joinpoint
  private final ThreadLocal<Deque<String>> jointPointStack = ThreadLocal.withInitial(ArrayDeque::new);

  // Keep thread-local SM attrs for SpanHelper
  private final ThreadLocal<Map<String, String>> stateMachineAttributes =
      ThreadLocal.withInitial(HashMap::new);

  // ---------- @Trace (REAL target executions only) ----------
  @Around("execution(* *(..)) && @annotation(at.ac.uibk.dps.cirrina.observability.tracing.Trace)")
  public Object traceAnnotated(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    Object target = proceedingJoinPoint.getTarget();
    // HARD STOP: if this is a Spring proxy surface, do not instrument here;
    // the woven method execution will be instrumented when proceed() hits the real class.
    if (isSpringProxy(target)) {
      return proceedingJoinPoint.proceed();
    }

    Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();
    Object[] args = proceedingJoinPoint.getArgs();

    Map<String, String> attributes =
        spanHelper.extractSpanAttributes(method, target, args, stateMachineAttributes.get());
    String spanName = attributes.containsKey("Name") ? attributes.remove("Name") : method.getName();

    MethodName methodName = method.getAnnotation(Trace.class).name();
    SpanKind kind = kindFor(methodName);

    String key = joinpointKey(method, proceedingJoinPoint);
    String parentKey = jointPointStack.get().peek();
    boolean isImmediateDuplicate = parentKey != null && parentKey.equals(key);
    jointPointStack.get().push(key);
    if (isImmediateDuplicate) {
      try {
        return proceedingJoinPoint.proceed();
      } finally {
        jointPointStack.get().pop();
      }
    }

    // Make SM attrs available to async children (e.g., HttpClient)
    Map<String, String> stateMachineAttributesCopy = new HashMap<>(stateMachineAttributes.get());
    Context contextWithStateMachine = Context.current().with(STATE_MACHINE_CONTEXT, stateMachineAttributesCopy);

    Span span = TRACING_HELPER.initializeSpanWithoutParent(spanName, TRACER, kind, attributes);
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        span.setAttribute(entry.getKey(), entry.getValue());
      }
    }

    try (Scope scope1 = contextWithStateMachine.makeCurrent(); Scope scope2 = span.makeCurrent()) {
      return proceedingJoinPoint.proceed();
    } catch (Throwable t) {
      TRACING_HELPER.recordException(t, span);
      throw t;
    } finally {
      span.end();
      jointPointStack.get().pop();
    }
  }

  // ---------- @TraceStatic (handleResponse) ----------
  @Around("@annotation(at.ac.uibk.dps.cirrina.observability.tracing.TraceStatic)")
  public Object traceStaticHandleResponse(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();
    String spanName = "SM " + stateMachineAttributes.get().get(ATTR_STATE_MACHINE_NAME) + " - " + method.getName(); // "handleResponse"

    String key = joinpointKey(method, proceedingJoinPoint);
    String parentKey = jointPointStack.get().peek();
    boolean isImmediateDuplicate = parentKey != null && parentKey.equals(key);
    jointPointStack.get().push(key);
    if (isImmediateDuplicate) {
      try {
        return proceedingJoinPoint.proceed();
      } finally {
        jointPointStack.get().pop();
      }
    }

    // Restore parent Context via HttpClientContextAspect
    Context parent = null;
    HttpRequest request = null;
    for (Object object : proceedingJoinPoint.getArgs()) {
      if (object instanceof HttpResponse<?> response) {
        try {
          request = response.request();
          parent = ContextStore.get(request);
        } catch (Throwable exception) {
          TRACING_HELPER.recordException(exception, null);
        }
        break;
      }
    }

    Span span = (parent != null)
        ? TRACING_HELPER.initializeSpanWithParent(spanName, TRACER, parent, SpanKind.INTERNAL)
        : TRACER.spanBuilder(spanName).setSpanKind(SpanKind.INTERNAL).startSpan();

    try (Scope scope = span.makeCurrent()) {
      Object[] args = proceedingJoinPoint.getArgs();
      if (args.length == 1 && args[0] instanceof HttpResponse<?> response) {
        Object body = response.body();
        String asString = (body instanceof byte[]) ? Arrays.toString((byte[]) body) : String.valueOf(body);
        span.setAttribute(ATTR_RESPONSE, safeTruncate(asString, 4096));
      }

      Map<String, String> stateMachineFromContext = (parent != null) ? parent.get(STATE_MACHINE_CONTEXT) : null;
      if (stateMachineFromContext != null) {
        for (Map.Entry<String, String> entry : stateMachineFromContext.entrySet()) {
          if (entry.getKey() != null && entry.getValue() != null) {
            span.setAttribute(entry.getKey(), entry.getValue());
          }
        }
        stateMachineAttributes.get().clear();
        stateMachineAttributes.get().putAll(stateMachineFromContext);
      }

      return proceedingJoinPoint.proceed();

    } catch (Throwable throwable) {
      TRACING_HELPER.recordException(throwable, span);
      throw throwable;
    } finally {
      span.end();
      jointPointStack.get().pop();
    }
  }

  // -------- helpers --------

  private static boolean isSpringProxy(Object target) {
    if (target == null) {
      return false;
    }
    Class<?> cls = target.getClass();
    if (Proxy.isProxyClass(cls)) {
      return true;
    }
    String name = cls.getName();
    return name.contains("$$SpringCGLIB$$") || name.contains("CGLIB$$");
  }

  private static String joinpointKey(Method method, JoinPoint pjp) {
    return method.getDeclaringClass().getName() + "#" + method.toGenericString() + "@" + sourceOf(pjp);
  }

  private static String sourceOf(JoinPoint joinPoint) {
    try {
      SourceLocation sourceLocation = joinPoint.getStaticPart().getSourceLocation();
      if (sourceLocation != null) {
        return sourceLocation.getFileName() + ":" + sourceLocation.getLine();
      }
    } catch (Throwable ignore) {
    }
    return "<unknown>";
  }

  private static SpanKind kindFor(MethodName name) {
    if (name == null) {
      return SpanKind.INTERNAL;
    }
    return switch (name) {
      case INVOKE -> SpanKind.CLIENT;
      case SEND_EVENT -> SpanKind.PRODUCER;
      case HANDLE_EVENT, ON_RECEIVE_EVENT -> SpanKind.CONSUMER;
      default -> SpanKind.INTERNAL;
    };
  }

  private static String safeTruncate(String string, int max) {
    if (string == null) {
      return null;
    }
    return string.length() <= max ? string : string.substring(0, max);
  }
}
