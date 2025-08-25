package at.ac.uibk.dps.cirrina.observability.logging;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.guard.Guard;
import at.ac.uibk.dps.cirrina.execution.object.state.State;
import at.ac.uibk.dps.cirrina.execution.object.transition.Transition;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.apache.logging.log4j.ThreadContext;

import java.util.Map;

public final class LoggingContext {

  private LoggingContext() {
  }

  public static void putOperation(String operation) {
    if (operation != null) {
      ThreadContext.put(ATTR_OPERATION, operation);
    }
  }

  public static void putStateMachineAttributes(TracingAttributes tracingAttributes) {
    if (tracingAttributes == null) {
      return;
    }
    if (tracingAttributes.getStateMachineId() != null) {
      ThreadContext.put(ATTR_STATE_MACHINE_ID, tracingAttributes.getStateMachineId());
    }
    if (tracingAttributes.getStateMachineName() != null) {
      ThreadContext.put(ATTR_STATE_MACHINE_NAME, tracingAttributes.getStateMachineName());
    }
    if (tracingAttributes.getParentStateMachineId() != null) {
      ThreadContext.put(ATTR_PARENT_STATE_MACHINE_ID, tracingAttributes.getParentStateMachineId());
    }
    if (tracingAttributes.getParentStateMachineName() != null) {
      ThreadContext.put(ATTR_PARENT_STATE_MACHINE_NAME, tracingAttributes.getParentStateMachineName());
    }
  }

  public static void putTracingFromMap(Map<String, String> tracingMap, String idKey, String nameKey) {
    if (tracingMap == null) {
      return;
    }
    String id = tracingMap.get(idKey);
    String name = tracingMap.get(nameKey);
    if (id != null) {
      ThreadContext.put(ATTR_STATE_MACHINE_ID, id);
    }
    if (name != null) {
      ThreadContext.put(ATTR_STATE_MACHINE_NAME, name);
    }
  }

  public static void putEvent(Event event) {
    if (event == null) {
      return;
    }
    if (event.getId() != null) {
      ThreadContext.put(ATTR_EVENT_ID, event.getId());
    }
    if (event.getName() != null) {
      ThreadContext.put(ATTR_EVENT_NAME, event.getName());
    }
    if (event.getChannel() != null) {
      ThreadContext.put(ATTR_EVENT_CHANNEL, event.getChannel().name());
    }
  }

  public static void putStateFromTo(State from, State to) {
    if (from != null && from.getStateObject() != null && from.getStateObject().getName() != null) {
      ThreadContext.put(ATTR_OLD_STATE, from.getStateObject().getName());
    }
    if (to != null && to.getStateObject() != null && to.getStateObject().getName() != null) {
      ThreadContext.put(ATTR_NEW_STATE, to.getStateObject().getName());
    }
  }

  public static void putNewState(State state) {
    if (state == null) {
      return;
    }
    if (state.getStateObject() != null) {
      ThreadContext.put(ATTR_NEW_STATE, state.getStateObject().getName());
    }
  }

  public static void putOldState(State state) {
    if (state == null) {
      return;
    }
    if (state.getStateObject() != null) {
      ThreadContext.put(ATTR_OLD_STATE, state.getStateObject().getName());
    }
  }

  public static void putActionName(String actionName) {
    if (actionName == null) {
      return;
    }
    ThreadContext.put(ATTR_ACTION_NAME, actionName);
  }

  public static void putOtelIdsIfAny() {
    SpanContext sc = Span.current().getSpanContext();
    if (sc != null && sc.isValid()) {
      ThreadContext.put(ATTR_TRACE_ID, sc.getTraceId());
      ThreadContext.put(ATTR_SPAN_ID, sc.getSpanId());
    }
  }

  public static void putTransitionStates(Transition transition) {
    if (transition == null) {
      return;
    }
    ThreadContext.put(ATTR_OLD_STATE, transition.getTransitionObject().getSource().getName());
    ThreadContext.put(ATTR_NEW_STATE, transition.getTransitionObject().getTarget().getName());
  }

  public static void putGuard(Guard guard) {
    if (guard == null) {
      return;
    }
    ThreadContext.put(ATTR_GUARD_EXPRESSION, guard.getExpression().toString());
  }

  public static void putService(String serviceName) {
    if (serviceName != null) {
      ThreadContext.put(ATTR_SERVICE_NAME, serviceName);
    }
  }

  public static void clear() {
    ThreadContext.clearMap();
  }
}
