package at.ac.uibk.dps.cirrina.observability.logging;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.*;

import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.guard.Guard;
import at.ac.uibk.dps.cirrina.execution.object.state.State;
import at.ac.uibk.dps.cirrina.execution.object.transition.Transition;
import at.ac.uibk.dps.cirrina.execution.object.statemachine.StateMachine;
import at.ac.uibk.dps.cirrina.observability.MethodName;
import at.ac.uibk.dps.cirrina.tracing.TracingAttributes;
import java.lang.reflect.Method;
import java.net.http.HttpResponse;
import java.util.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class LoggingAspect {

  private final LoggingHelper loggingHelper = new LoggingHelper();
  private final ThreadLocal<Map<String, String>> stateMachineAttributes = ThreadLocal.withInitial(HashMap::new);

  @Around("@annotation(at.ac.uibk.dps.cirrina.observability.logging.Log)")
  public Object loggingAdvice(ProceedingJoinPoint joinPoint) throws Throwable {

    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Object target = joinPoint.getTarget();
    Object[] args = joinPoint.getArgs();

    Log logAnnotation = method.getAnnotation(Log.class);
    MethodName methodName = logAnnotation.name();

    if (target instanceof StateMachine stateMachine) {
      var tracingAttributes = stateMachine.getTracingAttributes();
      stateMachineAttributes.get().clear();
      copyAttributes(stateMachineAttributes.get(), tracingAttributes);

      switch (methodName) {
        case ON_RECEIVE_EVENT:
          logEventReception(stateMachine, args, tracingAttributes);
          break;

        case TRY_SELECT_ON_TRANSITION: //
          loggingHelper.logOnTransitionSelection(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case TRY_SELECT_ALWAYS_TRANSITION://
          loggingHelper.logAlwaysTransitionSelection(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case TRY_SELECT_TRANSITION:
          loggingHelper.logTransitionSelection(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case EXECUTE:
          loggingHelper.logActionExecution(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case START_ALL_TIMEOUT_ACTIONS: //
          loggingHelper.logAllTimeoutActionsStartSM(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case STOP_ALL_TIMEOUT_ACTIONS: //
          loggingHelper.logAllTimeoutActionsStopSM(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case STOP_TIMEOUT_ACTION: //
          loggingHelper.logTimeoutActionStopSM(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case SWITCH_ACTIVE_STATE:
          logStateSwitch(stateMachine, args);
          break;

        case DO_EXIT:
          logStateExit(stateMachine, args);
          break;

        case DO_TRANSITION:
          logTransition(stateMachine, args);
          break;

        case DO_ENTER:
          logStateEntry(stateMachine, args);
          break;

        case HANDLE_INTERNAL_TRANSITION:
          loggingHelper.logInternalTransition(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case HANDLE_EXTERNAL_TRANSITION:
          loggingHelper.logExternalTransition(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case HANDLE_TRANSITION: //
          loggingHelper.logTransitionChoice(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          break;

        case HANDLE_EVENT:
          logEventHandling(stateMachine, args);
          break;

        case RUN:
          loggingHelper.logStateMachineStart(tracingAttributes.getStateMachineId(), tracingAttributes.getStateMachineName());
          logStateMachineStart(stateMachine);
          break;
      }
    } else {
      switch (methodName) {
        case EXECUTE_ACTION:
          logActionExecution(target, stateMachineAttributes.get());
          break;

        case START, STOP, STOP_ALL:
          logTimeoutStartAndStop(target, methodName, stateMachineAttributes.get());
          break;

        case INVOKE:
          logServiceInvocation(stateMachineAttributes.get());
          break;

        case HANDLE_RESPONSE:
          logResponseHandling(args, stateMachineAttributes.get());
          break;

        case EVALUATE:
          logGuardEvaluation(stateMachineAttributes.get(), target);
          break;

        case SEND_EVENT:
          logEventSending(args, stateMachineAttributes.get());
          break;

        case CREATE_ACTION_COMMAND:
          logActionCreation(args, stateMachineAttributes.get());
          break;

        case SELECT:
          loggingHelper.logServiceSelection(stateMachineAttributes.get().get(ATTR_STATE_MACHINE_ID),
              stateMachineAttributes.get().get(ATTR_STATE_MACHINE_NAME));
      }
    }

    try{
      return joinPoint.proceed();
    } catch (Throwable throwable) {
      loggingHelper.logException(stateMachineAttributes.get().get(ATTR_STATE_MACHINE_ID),
          throwable, stateMachineAttributes.get().get(ATTR_STATE_MACHINE_NAME));
      throw throwable;
    }
  }

  private void logEventReception(StateMachine sm, Object[] args, TracingAttributes tracingAttributes) {
    Event onReceiveEvent = (Event) args[0];
    loggingHelper.logEventReception(tracingAttributes.getStateMachineId(), onReceiveEvent,
        sm.getActiveStateName(), tracingAttributes.getStateMachineName());
  }

  private void logStateSwitch(StateMachine sm, Object[] args) {
    State newState = (State) args[0];
    loggingHelper.logActiveStateSwitch(
        sm.getTracingAttributes().getStateMachineId(),
        sm.getTracingAttributes().getStateMachineName(),
        sm.getActiveStateName(),
        newState.getStateObject().getName()
    );
  }

  private void logStateEntry(StateMachine sm, Object[] args) {
    State state = (State) args[0];
    Event event = (Event) args[1];
    loggingHelper.logStateEntry(sm.getTracingAttributes().getStateMachineId().toString(), sm.getClass().getName(),
        state.getStateObject().getName(), event);
  }

  private void logStateExit(StateMachine sm, Object[] args) {
    State state = (State) args[0];
    Event event = (Event) args[1];
    loggingHelper.logStateExit(sm.getTracingAttributes().getStateMachineId().toString(), sm.getClass().getName(),
        state.getStateObject().getName(), event);
  }

  private void logTransition(StateMachine sm, Object[] args) {
    Transition transition = (Transition) args[0];
    Event event = args[1] == null ? null : (Event) args[1];
    loggingHelper.logTransition(
        sm.getTracingAttributes().getStateMachineId(),
        sm.getTracingAttributes().getStateMachineName(),
        transition.getTransitionObject().getSource().getName(),
        transition.getTransitionObject().getTarget().getName(),
        event);
  }

  private void logEventHandling(StateMachine sm, Object[] args) {
    Event event = (Event) args[0];
    loggingHelper.logEventHandling(sm.getTracingAttributes().getStateMachineId(),
        sm.getTracingAttributes().getStateMachineName(), event);
  }

  private void logStateMachineStart(StateMachine sm) {
    loggingHelper.logStateMachineStart(sm.getTracingAttributes().getStateMachineId().toString(), sm.getClass().getName());
  }

  private void logActionExecution(Object target, Map<String, String> tracingAttributes){
    loggingHelper.logAction(target.getClass().getSimpleName(), tracingAttributes.get(ATTR_STATE_MACHINE_ID), tracingAttributes.get(ATTR_STATE_MACHINE_NAME));
  }

  private void logTimeoutStartAndStop(Object target, MethodName methodName, Map<String, String> tracingAttributes) {
    switch (methodName) {
      case START:
        loggingHelper.logTimeout("Start", target.getClass().getSimpleName(), tracingAttributes.get(ATTR_STATE_MACHINE_ID),
            tracingAttributes.get(ATTR_STATE_MACHINE_NAME));
        break;
      case STOP:
        loggingHelper.logTimeout("Stop", target.getClass().getSimpleName(), tracingAttributes.get(ATTR_STATE_MACHINE_ID),
            tracingAttributes.get(ATTR_STATE_MACHINE_NAME));

      case STOP_ALL:
        loggingHelper.logTimeout("Stop all", target.getClass().getSimpleName(), tracingAttributes.get(ATTR_STATE_MACHINE_ID),
            tracingAttributes.get(ATTR_STATE_MACHINE_NAME));

    }
  }

  private void logServiceInvocation(Map<String, String> tracingAttributes) {
    loggingHelper.logServiceInvocation("HTTP Service",
        tracingAttributes.get(ATTR_STATE_MACHINE_ID), tracingAttributes.get(ATTR_STATE_MACHINE_NAME));
  }

  private void logResponseHandling(Object[] args, Map<String, String> tracingAttributes) {
    HttpResponse response = (HttpResponse) args[0];
    loggingHelper.logServiceResponseHandling("HTTP Service", response,
        tracingAttributes.get(ATTR_STATE_MACHINE_ID),
        tracingAttributes.get(ATTR_STATE_MACHINE_NAME));
  }

  private void logGuardEvaluation(Map<String, String> tracingAttributes, Object target) {
    Guard guard = (Guard) target;
    var expression = guard.getExpression().toString();
    loggingHelper.logGuardEvaluation(expression, tracingAttributes.get(ATTR_STATE_MACHINE_NAME),
        tracingAttributes.get(ATTR_STATE_MACHINE_ID));

  }

  private void logEventSending(Object[] args, Map<String, String> tracingAttributes) {
    Event event = (Event) args[0];
    loggingHelper.logEventSending(event, tracingAttributes.get(ATTR_STATE_MACHINE_ID),
        tracingAttributes.get(ATTR_STATE_MACHINE_NAME));

  }

  private void logActionCreation(Object[] args, Map<String, String> tracingAttributes) {
    Action action = (Action) args[0];
    loggingHelper.logActionCreation(action.toString(), tracingAttributes.get(ATTR_STATE_MACHINE_ID),
        tracingAttributes.get(ATTR_STATE_MACHINE_NAME));
  }


  private void copyAttributes(Map<String, String> target, TracingAttributes src) {
    put(target, ATTR_STATE_MACHINE_ID, src.getStateMachineId());
    put(target, ATTR_STATE_MACHINE_NAME, src.getStateMachineName());
    put(target, ATTR_PARENT_STATE_MACHINE_NAME, src.getParentStateMachineName());
    put(target, ATTR_PARENT_STATE_MACHINE_ID, src.getParentStateMachineId());
  }

  private void put(Map<String, String> map, String key, String value) {
    if (value != null) map.put(key, value);
  }

}