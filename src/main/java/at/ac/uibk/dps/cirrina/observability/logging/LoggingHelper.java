package at.ac.uibk.dps.cirrina.observability.logging;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import java.net.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoggingHelper {

  private static final Logger logger = LogManager.getLogger(LoggingHelper.class);

  public void logStateMachineStart(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Starting", stateMachineName, stateMachineId);
  }

  public void logException(String stateMachineId, Throwable ex, String stateMachineName) {
    logger.error("State Machine {} ({}): Exception occurred {}", stateMachineName, stateMachineId, ex.toString(), ex);
  }

  public void logAction(String actionName, String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Executing Action {}", stateMachineName, stateMachineId, actionName);
  }

  public void logActionExecution(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Executing Actions", stateMachineName, stateMachineId);
  }

  public void logTimeout(String type, String actionName, String stateMachineId, String stateMachineName) {
    switch (type) {
      case "Start":
        logger.info("State Machine {} ({}): Starting Timeout Action {}", stateMachineName, stateMachineId, actionName);
        break;

      case "Stop":
        logger.info("State Machine {} ({}): Stopping Timeout Action {}", stateMachineName, stateMachineId, actionName);
        break;

      case "Stop all":
        logger.info("State Machine {} ({}): Stopping all Timeout Actions", stateMachineName, stateMachineId);
        break;

      default:
        logger.debug("Timeout '{}' action='{}' (smName='{}' smId='{}')", type, actionName, stateMachineName, stateMachineId);
        break;
    }
  }

  public void logServiceInvocation(String serviceName, String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Service Invocation: {}", stateMachineName, stateMachineId, serviceName);
  }

  public void logServiceResponseHandling(String serviceName, HttpResponse response, String stateMachineId, String stateMachineName) {
    if (response == null) {
      logger.info("State Machine {} ({}): Handling Service Response: {} with Body null",
          stateMachineName, stateMachineId, serviceName);
      return;
    }

    logger.info("State Machine {} ({}): Handling Service Response: {} with Status Code {}",
        stateMachineName, stateMachineId, serviceName, response.statusCode());

    if (logger.isDebugEnabled()) {
      try {
        logger.debug("Service response headers: {}", response.headers().map());
        Object body = response.body();
        if (body != null) {
          String bodyPreview = truncate(String.valueOf(body), 8192);
          logger.debug("Service response body (preview <=8192B): {}", bodyPreview);
        }
      } catch (Throwable ex) {
        logException(stateMachineId, ex, stateMachineName);
      }
    }
  }

  public void logEventReception(String stateMachineId, Event event, String state, String stateMachineName) {
    logger.info("State Machine {} ({}): Receiving event {} ({}) in State {}", stateMachineName, stateMachineId,
        event != null ? event.getName() : "null",
        event != null ? event.getId() : "null", state);
  }

  public void logActiveStateSwitch(String stateMachineId, String stateMachineName, String currentState, String newState) {
    logger.info("State Machine {} ({}): Switching from {} to {}", stateMachineName, stateMachineId, currentState, newState);
  }

  public void logStateExit(String stateMachineId, String stateMachineName, String exitedState, Event event) {
    if (event != null) {
      logger.info("State Machine {} ({}): Exiting from State {} due to Event: {} ({}) ",
          stateMachineName, stateMachineId, exitedState, event.getName(), event.getId());
    } else {
      logger.info("State Machine {} ({}): Exiting from State {}", stateMachineName, stateMachineId, exitedState);
    }
  }

  public void logStateEntry(String stateMachineId, String stateMachineName, String enteringState, Event event) {
    if (event != null) {
      logger.info("State Machine {} ({}): Entered State {} due to Event: {} ({}) ",
          stateMachineName, stateMachineId, enteringState, event.getName(), event.getId());
    } else {
      logger.info("State Machine {} ({}): Entering State {}", stateMachineName, stateMachineId, enteringState);
    }
  }

  public void logTransition(String stateMachineId, String stateMachineName, String fromState, String toState, Event event) {
    if (event != null) {
      logger.info("State Machine {} ({}): Transitioning from State {} to State {} due to Event: {} ({})",
          stateMachineName, stateMachineId, fromState, toState, event.getName(), event.getId());
    } else {
      logger.info("State Machine {} ({}): Transitioning from State {} to State {}",
          stateMachineName, stateMachineId, fromState, toState);
    }
  }

  public void logEventHandling(String stateMachineId, String stateMachineName, Event event) {
    logger.info("State Machine {} ({}): Handling Event: {} ({})",
        stateMachineName, stateMachineId,
        event != null ? event.getName() : "null",
        event != null ? event.getId() : "null");
  }

  public void logGuardEvaluation(String expression, String stateMachineName, String stateMachineId) {
    logger.info("State Machine {} ({}): Evaluating Guard with Expression: {}", stateMachineName, stateMachineId, expression);
  }

  public void logEventSending(Event event, String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Sending Event {} ({})", stateMachineName, stateMachineId,
        event != null ? event.getName() : "null",
        event != null ? event.getId() : "null");
  }

  public void logActionCreation(String actionName, String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Action {} created!", stateMachineName, stateMachineId, actionName);
  }

  public void logTransitionSelection(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Selecting Transition", stateMachineName, stateMachineId);
  }

  public void logOnTransitionSelection(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Selecting On Transition", stateMachineName, stateMachineId);
  }

  public void logAlwaysTransitionSelection(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Selecting Always Transition", stateMachineName, stateMachineId);
  }

  public void logAllTimeoutActionsStartSM(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Starting all Timeout Actions", stateMachineName, stateMachineId);
  }

  public void logTimeoutActionStopSM(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Stoping Timeout Action", stateMachineName, stateMachineId);
  }

  public void logAllTimeoutActionsStopSM(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Stoping all Timeout Actions", stateMachineName, stateMachineId);
  }

  public void logExternalTransition(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Handling External Transition", stateMachineName, stateMachineId);
  }

  public void logInternalTransition(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Handling Internal Transition", stateMachineName, stateMachineId);
  }

  public void logTransitionHandling(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Handling Transition", stateMachineName, stateMachineId);
  }

  public void logServiceSelection(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Selecting Service", stateMachineName, stateMachineId);
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max) + "...(truncated)";
  }

}