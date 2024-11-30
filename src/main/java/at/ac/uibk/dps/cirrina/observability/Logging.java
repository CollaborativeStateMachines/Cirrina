package at.ac.uibk.dps.cirrina.observability;

import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import java.net.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Logging {

  public final Logger logger = LogManager.getLogger();

  public void logStateMachineStart(String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}) starting", stateMachineName, stateMachineId);
  }

  public void logExeption(String stateMachineId, Throwable ex, String stateMachineName) {
    logger.error("State Machine {} ({}) {}", stateMachineName, stateMachineId, ex.getMessage(), ex);

  }

  public void logAction(String actionName, String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}) executing action {}", stateMachineName, stateMachineId, actionName);

  }

  public void logTimeout(String type, String actionName, String stateMachineId, String stateMachineName) {
    switch (type) {
      case "Start":
        logger.info("State Machine {} ({}) starting timeout {}", stateMachineName, stateMachineId, actionName);

      case "Stop":
        logger.info("State Machine {} ({}) stopping {}", stateMachineName, stateMachineId, actionName);

      case "Stop all":
        logger.info("State Machine {} ({}) stopping all timeout actions", stateMachineName, stateMachineId);

    }

  }

  public void logServiceInvocation(String serviceName, String stateMachineId, String stateMachineName) {
    logger.info("State Machine {} ({}): Service invocation: {}", stateMachineName, stateMachineId, serviceName);
  }

  public void logServiceResponseHandling(String serviceName, HttpResponse response, String stateMachineId, String stateMachineName){
    logger.info("State Machine {} ({}): Handling service response: {} with status code {} and body {}",
        stateMachineName, stateMachineId, serviceName, response.statusCode(), response.body().toString());

  }

  public void logEventReception(String stateMachineId, Event event, String state, String stateMachineName){
    logger.info("State Machine {} ({}) received event {} ({}) in State {}", stateMachineName, stateMachineId, event.getName(), event.getId(), state);

  }

  public void logActiveStateSwitch(String stateMachineId, String stateMachineName, String currentState, String newState){
    logger.info("State Machine {} ({}) switched from {} to {}", stateMachineName, stateMachineId, currentState, newState);


  }

  public void logStateExit(String stateMachineId, String stateMachineName, String exitedState, Event event){
    if (event != null) {
      logger.info("State Machine {} ({}) exiting from state {} due to Event: {} ({}) ",
          stateMachineName, stateMachineId, exitedState, event.getName(), event.getId());
    } else {
      logger.info("State Machine {} ({}) exiting from state {}", stateMachineName, stateMachineId, exitedState);
    }


  }

  public void logStateEntry(String stateMachineId, String stateMachineName, String enteringState, Event event){
    if (event != null) {
      logger.info("State Machine {} ({}) entered state {} due to Event: {} ({}) ",
          stateMachineName, stateMachineId, enteringState, event.getName(), event.getId());
    } else {
      logger.info("State Machine {} ({}) entering state {}", stateMachineName, stateMachineId, enteringState);
    }
  }

  public void logTransition(String stateMachineId, String stateMachineName, String fromState, String toState, Event event){
    if (event != null) {
      logger.info("State Machine {} ({}) transitioning from state {} to {} due to Event: {} ({})",
          stateMachineName, stateMachineId, fromState, toState, event.getName(), event.getId());
    } else {
      logger.info("State Machine {} ({}) transitioning from state {} to {}",
          stateMachineName, stateMachineId, fromState, toState);
    }


  }

  public void logEventHandling(String stateMachineId, String stateMachineName, Event event){
    logger.info("State Machine {} ({}) handling Event: {} ({})",
        stateMachineName, stateMachineId, event.getName(), event.getId());

  }

  public void logGuardEvaluation(String expression, String stateMachineName, String stateMachineId){
    logger.info("State Machine {} ({}) evaluating guard with expression: {}", stateMachineName, stateMachineId, expression);

  }

  public void logEventSending(Event event, String stateMachineId, String stateMachineName){
    logger.info("State Machine {} ({}) sending event {} ({})", stateMachineName, stateMachineId, event.getName(), event.getId());
  }

  public void logActionCreation(String actionName) {
    logger.info("Action {} created!", actionName);
  }

}