package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.EventChannel;
import at.ac.uibk.dps.cirrina.execution.object.action.RaiseAction;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.observability.MethodName;
import at.ac.uibk.dps.cirrina.observability.logging.Log;
import at.ac.uibk.dps.cirrina.observability.tracing.Trace;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ActionRaiseCommand extends ActionCommand {

  private static final Logger logger = LogManager.getLogger();

  private final RaiseAction raiseAction;

  private final AtomicReference<Double> latency = new AtomicReference<>();

  ActionRaiseCommand(ExecutionContext executionContext, RaiseAction raiseAction) {
    super(executionContext);

    this.raiseAction = raiseAction;
  }

  @Trace(name = MethodName.EXECUTE_ACTION)
  @Log(name = MethodName.EXECUTE_ACTION)
  @Override
  public List<ActionCommand> execute() throws UnsupportedOperationException {
    final var commands = new ArrayList<ActionCommand>();

    try {
      final var event = raiseAction.getEvent();

      final var extent = executionContext.scope().getExtent();
      final var eventHandler = executionContext.eventHandler();
      final var eventListener = executionContext.eventListener();

      final var evaluatedEvent = Event.ensureHasEvaluatedData(event, extent);

      // Dispatch the event
      if (evaluatedEvent.getChannel() == EventChannel.INTERNAL) {
        eventListener.onReceiveEvent(evaluatedEvent);
      } else {
        // Send the event through the event handler
        eventHandler.sendEvent(evaluatedEvent);
      }
    } catch (IOException e) {
      logger.error("Data creation failed: {}", e.getMessage());
    }

    return commands;
  }
}
