package at.ac.uibk.dps.cirrina.observability.tracing;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*;

import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.guard.Guard;
import at.ac.uibk.dps.cirrina.execution.object.state.State;
import at.ac.uibk.dps.cirrina.execution.object.statemachine.StateMachine;
import at.ac.uibk.dps.cirrina.execution.object.transition.Transition;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class SpanHelper {

  Map<String, String> extractSpanAttributes(Method method, Object target, Object[] args, Map<String, String> stateMachineAttributes) {
    Map<String, String> spanAttributes = new HashMap<>();
    Trace traceAnnotation = method.getAnnotation(Trace.class);
    MethodName methodAnnotation = traceAnnotation.name();
    String calledMethodName = method.getName();



    if (target instanceof StateMachine stateMachine) {

      var tracingAttributes = stateMachine.getTracingAttributes();
      stateMachineAttributes.clear();
      copyTracingAttributes(stateMachineAttributes, tracingAttributes);
      insertTracingAttributes(stateMachineAttributes, spanAttributes);

      switch (methodAnnotation) {
        case DO_ENTER, DO_EXIT:
          handleEnterOrExit(spanAttributes, calledMethodName, stateMachine, args);
          break;

        case ON_RECEIVE_EVENT, TRY_SELECT_ON_TRANSITION, HANDLE_EVENT:
          handleEventReceptionHandlingAndOnTransitions(args, calledMethodName, stateMachine, spanAttributes);
          break;

        case TRY_SELECT_ALWAYS_TRANSITION, TRY_SELECT_TRANSITION, EXECUTE, START_ALL_TIMEOUT_ACTIONS, STOP_ALL_TIMEOUT_ACTIONS:
          handleAlwaysTransitionsAndStartStopAllTimeouts(spanAttributes, stateMachine, calledMethodName);
          break;

        case STOP_TIMEOUT_ACTION:
          handleTimeoutStop(args, calledMethodName, stateMachine, spanAttributes);
          break;

        case SWITCH_ACTIVE_STATE:
          handleStateSwitch(args, calledMethodName, stateMachine, spanAttributes);
          break;

        case DO_TRANSITION, HANDLE_INTERNAL_TRANSITION, HANDLE_EXTERNAL_TRANSITION, HANDLE_TRANSITION:
          handleTransition(args, calledMethodName, stateMachine, spanAttributes);
          break;

      }

    } else {

      insertTracingAttributes(stateMachineAttributes, spanAttributes);
      switch (methodAnnotation) {

        case EXECUTE_ACTION:
          handleActionExecution(calledMethodName, spanAttributes, target);
          break;

        case CREATE_ACTION_COMMAND:
          handleActionCreation(args, calledMethodName, spanAttributes);
          break;

        case EVALUATE:
          handleGuardEvaluation(target, calledMethodName, spanAttributes);
          break;

        case SEND_EVENT:
          handleEventSending(args, calledMethodName, spanAttributes);
          break;

        case START, STOP:
          handleStartStop(args, calledMethodName, spanAttributes);
          break;

        case STOP_ALL:
          handleStopAll(calledMethodName, spanAttributes);
          break;

        case INVOKE:
          handleServiceInvocation(args, calledMethodName, spanAttributes);
          break;

        case SELECT:
          handleServiceSelection(args, calledMethodName, spanAttributes);
          break;
      }
    }
    return spanAttributes;
  }

  private void spanNameCreator(String methodName, Map<String, String> attributes, String additionalInfo) {
    put(attributes, "Name", "SM " + attributes.get(ATTR_STATE_MACHINE_NAME) + " - " + methodName +
        " " + (additionalInfo != null ? additionalInfo : ""));

  }

  private void handleEnterOrExit(Map<String, String> attributes, String methodName, StateMachine stateMachine, Object[] args){
    State state = (State) args[0];
    Event raisingEvent = (Event) args[1];
    spanNameCreator(methodName, attributes, null);
    put(attributes, ATTR_EVENT_NAME, raisingEvent != null ? raisingEvent.getName() : "null");
    put(attributes, ATTR_EVENT_ID, raisingEvent != null ? raisingEvent.getId() : "null");

    if(methodName.equals("doEnter")) {
      put(attributes, ATTR_NEW_STATE, state.getStateObject().getName());
      put(attributes, ATTR_OLD_STATE, stateMachine.getActiveStateName());
    } else {
      put(attributes, ATTR_ACTIVE_STATE, stateMachine.getActiveStateName());
      put(attributes, ATTR_OLD_STATE, state.getStateObject().getName());
    }

  }

  private void handleEventReceptionHandlingAndOnTransitions(Object[] args, String methodName, StateMachine stateMachine, Map<String, String> attributes) {
    Event event = (Event) args[0];
    spanNameCreator(methodName, attributes, null);
    put(attributes, ATTR_EVENT_NAME, event.getName());
    put(attributes, ATTR_EVENT_ID, event.getId());
    put(attributes, ATTR_ACTIVE_STATE, stateMachine.getActiveStateName());


  }

  private void handleAlwaysTransitionsAndStartStopAllTimeouts(Map<String, String> attributes, StateMachine stateMachine, String calledMethodName){
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_ACTIVE_STATE, stateMachine.getActiveStateName());
  }

  private void handleTimeoutStop(Object[] args, String calledMethodName, StateMachine stateMachine, Map<String, String> attributes) {
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_ACTION_NAME, (String) args[0]);
    put(attributes, ATTR_ACTIVE_STATE, stateMachine.getActiveStateName());
  }

  private void handleStateSwitch(Object[] args, String calledMethodName, StateMachine stateMachine, Map<String, String> attributes) {
    State new_state = (State) args[0];
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_NEW_STATE, new_state.getStateObject().getName());
    put(attributes, ATTR_OLD_STATE, stateMachine.getActiveStateName());
  }

  private void handleTransition(Object[] args, String calledMethodName, StateMachine stateMachine, Map<String, String> attributes) {
    Transition transition = (Transition) args[0];
    Event transition_event =  (Event) args[1];
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_SOURCE_STATE, transition.getTransitionObject().getSource().getName());
    put(attributes, ATTR_TARGET_STATE, transition.getTransitionObject().getTarget().getName());
    put(attributes, ATTR_EVENT_NAME, transition_event != null ? transition_event.getName() : "null");
    put(attributes, ATTR_EVENT_ID, transition_event != null ? transition_event.getId() : "null");
    put(attributes, ATTR_ACTIVE_STATE, stateMachine.getActiveStateName());
    if (calledMethodName.equals("handleInternalTransition")) {
      put(attributes, ATTR_TRANSITION_INTERNAL, "true");
    }  else if (calledMethodName.equals("handleExternalTransition")) {
      put(attributes, ATTR_TRANSITION_INTERNAL, "false");
    } else {
      put(attributes, ATTR_TRANSITION_INTERNAL, String.valueOf(transition.isInternalTransition()));
    }
  }

  private void handleActionExecution(String calledMethodName, Map<String, String> attributes, Object target) {
    spanNameCreator(calledMethodName, attributes, "action");
    put(attributes, ATTR_ACTION_NAME, target.getClass().getSimpleName());
  }

  private void handleActionCreation(Object[] args, String calledMethodName, Map<String, String> attributes) {
    Action action = (Action) args[0];
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_ACTION_NAME, action.toString());
  }

  private void handleGuardEvaluation(Object target, String calledMethodName, Map<String, String> attributes) {
    Guard guard = (Guard) target;
    var expr = guard.getExpression().toString();
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_GUARD_EXPRESSION, expr);
  }

  private void handleEventSending(Object[] args, String calledMethodName, Map<String, String> attributes) {
    Event event = (Event) args[0];
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_EVENT_NAME, event.getName());
    put(attributes, ATTR_EVENT_ID, event.getId());
  }

  private void handleStartStop(Object[] args, String calledMethodName, Map<String, String> attributes) {
    String actionName = (String) args[0];
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_ACTION_NAME, actionName);
  }

  private void handleStopAll(String calledMethodName, Map<String, String> attributes) {
    spanNameCreator(calledMethodName, attributes, null);
  }

  private void handleServiceInvocation(Object[] args, String calledMethodName, Map<String, String> attributes) {
    String id = (String) args[1];
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_INVOKED_BY, id);
  }

  private void handleServiceSelection(Object[] args, String calledMethodName, Map<String, String> attributes) {
    String name = (String) args[0];
    boolean local =  (boolean) args[1];
    spanNameCreator(calledMethodName, attributes, null);
    put(attributes, ATTR_SERVICE_NAME, name);
    put(attributes, ATTR_IS_LOCAL, String.valueOf(local));
  }


  private void copyTracingAttributes(Map<String, String> target, TracingAttributes src) {
    put(target, ATTR_STATE_MACHINE_ID, src.getStateMachineId());
    put(target, ATTR_STATE_MACHINE_NAME, src.getStateMachineName());
    put(target, ATTR_PARENT_STATE_MACHINE_NAME, src.getParentStateMachineName());
    put(target, ATTR_PARENT_STATE_MACHINE_ID, src.getParentStateMachineId());
  }

  private void insertTracingAttributes(Map<String, String> source, Map<String, String> target) {
    put(target, ATTR_STATE_MACHINE_ID, source.get(ATTR_STATE_MACHINE_ID));
    put(target, ATTR_STATE_MACHINE_NAME, source.get(ATTR_STATE_MACHINE_NAME));
    put(target, ATTR_PARENT_STATE_MACHINE_NAME, source.get(ATTR_PARENT_STATE_MACHINE_NAME));
    put(target, ATTR_PARENT_STATE_MACHINE_ID, source.get(ATTR_PARENT_STATE_MACHINE_ID));
  }

  private void put(Map<String, String> map, String key, String value) {
    if (value != null) map.put(key, value);
  }


}
