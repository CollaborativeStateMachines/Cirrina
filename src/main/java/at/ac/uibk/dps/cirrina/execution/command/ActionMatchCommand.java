package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.execution.object.action.MatchAction;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ActionMatchCommand extends ActionCommand {

  private static final Logger logger = LogManager.getLogger();

  private final MatchAction matchAction;

  ActionMatchCommand(ExecutionContext executionContext, MatchAction matchAction) {
    super(executionContext);
    this.matchAction = matchAction;
  }

  @Override
  public List<ActionCommand> execute() throws UnsupportedOperationException {
    final var commands = new ArrayList<ActionCommand>();

    try {
      final var extent = executionContext.scope().getExtent();
      final var conditionValue = matchAction.getValue().execute(extent);

      final var commandFactory = new CommandFactory(executionContext);

      // Find matching conditions and append the commands to the set of new commands
      for (var entry : matchAction.getCase().entrySet()) {
        final var caseValue = entry.getKey().execute(extent);
        final var caseAction = entry.getValue();

        // In case the case condition matches, add the case action
        if (conditionValue == caseValue) {
          final var command = commandFactory.createActionCommand(caseAction);

          commands.add(command);
        }
      }
    } catch (UnsupportedOperationException e) {
      logger.error("Could not execute match action: {}", e.getMessage());
    }

    return commands;
  }
}
