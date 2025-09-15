package at.ac.uibk.dps.cirrina.execution.command;

import at.ac.uibk.dps.cirrina.cirrina.Runtime;
import at.ac.uibk.dps.cirrina.execution.object.action.SpawnAction;
import at.ac.uibk.dps.cirrina.execution.object.statemachine.StateMachine;
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

    // Retrieve the parent state machine
    final String parentStateMachineId = executionContext.scope().getId();
    final StateMachine parentStateMachine = runtime.findInstance(parentStateMachineId);

    // A parent instance needs to exist for spawning a new state machine instance
    if (parentStateMachine == null) {
      logger.error("Parent instance with ID {} not found", parentStateMachineId);
      return List.of();
    }

    final Id parentId = parentStateMachine.getStateMachineInstanceId();

    final var instanceId = runtime.newInstances(
      List.of(stateMachine),
      executionContext.serviceImplementationSelector(),
      parentId,
      executionContext.scope().getExtent()
    );

    return List.of();
  }
}
