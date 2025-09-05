package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.execution.object.action.SpawnAction;
import at.ac.uibk.dps.cirrina.execution.object.statemachine.StateMachine;
import at.ac.uibk.dps.cirrina.runtime.Runtime;
import at.ac.uibk.dps.cirrina.utils.Id;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ActionSpawnCommand extends ActionCommand {

  private static final Logger logger = LogManager.getLogger();

  private final SpawnAction spawnAction;

  ActionSpawnCommand(ExecutionContext executionContext, SpawnAction spawnAction) {
    super(executionContext);
    this.spawnAction = spawnAction;
  }

  @Override
  public List<ActionCommand> execute() throws UnsupportedOperationException {
    final var stateMachine = spawnAction.getStateMachine();
    final Runtime runtime = executionContext.runtime();

    // Retrieve the parent runtime
    final var parentInstanceId = executionContext.scope().getId();
    final Optional<StateMachine> parentInstance = runtime.findInstance(parentInstanceId);

    // A parent instance needs to exist for spawning a new state machine instance
    if (parentInstance.isEmpty()) {
      logger.error("Parent instance with ID {} not found", parentInstanceId);
      return List.of();
    }

    final Id parentId = parentInstance.get().getStateMachineInstanceId();

    final var instanceId = runtime.newInstances(
      List.of(stateMachine),
      executionContext.serviceImplementationSelector(),
      parentId,
      0,
      executionContext.scope().getExtent()
    );

    return List.of();
  }
}
