package at.ac.uibk.dps.cirrina.execution.object.statemachine;

import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.COUNTER_EVENTS_HANDLED;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.COUNTER_EVENTS_RECEIVED;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.COUNTER_INVOCATIONS;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.COUNTER_STATE_MACHINE_INSTANCES;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ACTION_DATA_LATENCY;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ACTION_INVOKE_LATENCY;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_ACTION_RAISE_LATENCY;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_EVENT_RESPONSE_TIME_EXCLUSIVE;
import static at.ac.uibk.dps.cirrina.tracing.SemanticConvention.GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE;

import at.ac.uibk.dps.cirrina.classes.state.StateClass;
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass;
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass;
import at.ac.uibk.dps.cirrina.csml.description.Csml.EventChannel;
import at.ac.uibk.dps.cirrina.execution.command.ActionCommand;
import at.ac.uibk.dps.cirrina.execution.command.ActionRaiseCommand;
import at.ac.uibk.dps.cirrina.execution.command.CommandFactory;
import at.ac.uibk.dps.cirrina.execution.command.ExecutionContext;
import at.ac.uibk.dps.cirrina.execution.command.Scope;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import at.ac.uibk.dps.cirrina.execution.object.context.Context;
import at.ac.uibk.dps.cirrina.execution.object.context.ContextBuilder;
import at.ac.uibk.dps.cirrina.execution.object.context.Extent;
import at.ac.uibk.dps.cirrina.execution.object.context.InMemoryContext;
import at.ac.uibk.dps.cirrina.execution.object.event.Event;
import at.ac.uibk.dps.cirrina.execution.object.event.EventListener;
import at.ac.uibk.dps.cirrina.execution.object.state.State;
import at.ac.uibk.dps.cirrina.execution.object.transition.Transition;
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationSelector;
import at.ac.uibk.dps.cirrina.runtime.Runtime;
import at.ac.uibk.dps.cirrina.tracing.Counters;
import at.ac.uibk.dps.cirrina.tracing.Gauges;
import at.ac.uibk.dps.cirrina.utils.Id;
import at.ac.uibk.dps.cirrina.utils.Time;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StateMachine implements Runnable, EventListener, Scope {

  /**
   * Event data variable prefix, prepended to an even data variable name to indicate that the variable resembles event data.
   */
  public static final String EVENT_DATA_VARIABLE_PREFIX = "$";

  /**
   * State machine logger.
   */
  private static final Logger logger = LogManager.getLogger();

  /**
   * State machine ID.
   */
  private final Id stateMachineId = new Id();

  /**
   * Timeout action manager, contains currently running timeout actions.
   */
  private final TimeoutActionManager timeoutActionManager = new TimeoutActionManager();

  /**
   * Event queue, contains events received by the state machine.
   */
  private final Queue<Event> eventQueue = new ConcurrentLinkedQueue<>();

  /**
   * Parent runtime.
   */
  private final Runtime parentRuntime;

  /**
   * State machine class of this instance.
   */
  private final StateMachineClass stateMachineClass;

  /**
   * Selector of service implementations, based on service types.
   */
  private final ServiceImplementationSelector serviceImplementationSelector;

  /**
   * Parent state machine instance or null in case this state machine instance is a parent.
   */
  private final @Nullable StateMachine parentStateMachine;

  /**
   * End time of this state machine in milliseconds since the start of the runtime.
   */
  private final double endTimeInMs;

  private final StateMachineEventHandler stateMachineEventHandler;

  private final Context localContext;

  private final Map<String, State> stateInstances;

  private final Gauges gauges;

  private final Counters counters;

  private State activeState;

  private List<Id> nestedStateMachineIds = new ArrayList<>();

  /**
   * Initializes this state machine instance object. A state machine instance is associated with a state machine object that describes its
   * static structure.
   * <p>
   * A state machine instance is parented in a runtime.
   * <p>
   * A service implementation selector is provided that allows selecting between the service implementations accessible to this state
   * machine instance.
   * <p>
   * Additionally, a state machine instance may be parented in a state machine instance, forming a nested state machine instance.
   *
   * @param parentRuntime                 Parent runtime.
   * @param stateMachineClass             StateClass machine object
   * @param serviceImplementationSelector Service implementation selector.
   * @param parentStateMachine            Parent state machine instance or null.
   * @param endTimeInMs                   The end time of this state machine in milliseconds since the start of the runtime.
   * @thread Runtime.
   */
  public StateMachine(
    Runtime parentRuntime,
    StateMachineClass stateMachineClass,
    ServiceImplementationSelector serviceImplementationSelector,
    OpenTelemetry openTelemetry,
    @Nullable StateMachine parentStateMachine,
    double endTimeInMs
  ) {
    this.parentRuntime = parentRuntime;
    this.stateMachineClass = stateMachineClass;
    this.serviceImplementationSelector = serviceImplementationSelector;
    this.parentStateMachine = parentStateMachine;
    this.endTimeInMs = endTimeInMs;

    stateMachineEventHandler = new StateMachineEventHandler(
      this,
      this.parentRuntime.getEventHandler()
    );

    // Build the local context
    try {
      localContext = stateMachineClass
        .getLocalContextClass()
        .map(ContextBuilder::from)
        .orElseGet(ContextBuilder::from)
        .inMemoryContext(true)
        .build();
    } catch (IOException ignored) {
      throw new IllegalStateException(); // This should not happen
    }

    // Construct state instances
    stateInstances = stateMachineClass
      .vertexSet()
      .stream()
      .collect(Collectors.toMap(StateClass::getName, state -> new State(state, this)));

    // Create an OpenTelemetry meter
    final var meter = openTelemetry.getMeter(
      "stateMachine-%s".formatted(stateMachineId.toString())
    );

    // Create gauges
    gauges = new Gauges(meter, getId());

    gauges.addGauge(GAUGE_EVENT_RESPONSE_TIME_EXCLUSIVE);
    gauges.addGauge(GAUGE_EVENT_RESPONSE_TIME_INCLUSIVE);
    gauges.addGauge(GAUGE_ACTION_DATA_LATENCY);
    gauges.addGauge(GAUGE_ACTION_INVOKE_LATENCY);
    gauges.addGauge(GAUGE_ACTION_RAISE_LATENCY);

    // Create counters
    counters = new Counters(meter, getId());

    counters.addCounter(COUNTER_EVENTS_RECEIVED);
    counters.addCounter(COUNTER_EVENTS_HANDLED);
    counters.addCounter(COUNTER_INVOCATIONS);
    counters.addCounter(COUNTER_STATE_MACHINE_INSTANCES);
  }

  /**
   * Handles a received event.
   *
   * @param event Received event.
   * @thread Events.
   */
  @Override
  public boolean onReceiveEvent(Event event) {
    // Nothing to do if the state machine is terminated
    if (isTerminated()) {
      return false;
    }

    // Increment events received counter
    counters
      .getCounter(COUNTER_EVENTS_RECEIVED)
      .add(1, counters.attributesForEvent(event.getChannel().toString()));

    // Add to the internal event queue
    eventQueue.add(event);

    synchronized (this) {
      notify();
    }

    // Propagate internal events to nested state machines
    if (event.getChannel() == EventChannel.INTERNAL) {
      for (final var nestedStateMachineId : nestedStateMachineIds) {
        final var nestedStateMachineInstance = parentRuntime
          .findInstance(nestedStateMachineId)
          .orElseThrow(() ->
            new IllegalStateException(
              "Nested state machine could not be found, could not propagate event"
            )
          );

        nestedStateMachineInstance.onReceiveEvent(event);
      }
    }

    return true;
  }

  /**
   * Returns a flag that indicates if this state machine instance is terminated.
   *
   * @return True if the state machine instance is terminated, otherwise false.
   */
  public boolean isTerminated() {
    // The active state is null initially, this indicates that the state machine instance is not terminated
    if (activeState == null) {
      return false;
    }

    // The state machine instance should be terminated if it is nested and its parent is terminated.
    if (parentStateMachine != null && parentStateMachine.isTerminated()) {
      return true;
    }

    // The state machine instance should be terminated if its end time has passed
    final var currentTime = Time.timeInMillisecondsSinceStart();

    if (endTimeInMs > 0.0 && currentTime > endTimeInMs) {
      return true;
    }

    return activeState.getStateObject().isTerminal();
  }

  /**
   * Creates a state machine instance-scoped command factory.
   *
   * @param stateMachine StateClass machine instance (scope).
   * @param raisingEvent The raising event.
   * @return Command factory.
   */
  private CommandFactory stateMachineScopedCommandFactory(
    StateMachine stateMachine,
    @Nullable Event raisingEvent
  ) {
    return new CommandFactory(
      new ExecutionContext(
        stateMachine, // Scope
        raisingEvent, // Raising event
        serviceImplementationSelector, // Service implementation selector
        stateMachineEventHandler, // Event handler
        this, // Event listener
        gauges, // Gauges
        counters, // Counters
        false // Is while?
      )
    );
  }

  /**
   * Creates a state instance-scoped command factory.
   *
   * @param state        StateClass instance (scope).
   * @param raisingEvent The raising event.
   * @param isWhile      Whether the command factory build while actions.
   * @return Command factory.
   */
  private CommandFactory stateScopedCommandFactory(
    State state,
    @Nullable Event raisingEvent,
    boolean isWhile
  ) {
    return new CommandFactory(
      new ExecutionContext(
        state, // Scope
        raisingEvent, // Raising event
        serviceImplementationSelector, // Service implementation selector
        stateMachineEventHandler, // Event handler
        this, // Event listener
        gauges, // Gauges
        counters, // Counters
        false // Is while?
      )
    );
  }

  /**
   * Attempts to return a state instance by name.
   *
   * @param name Name to find.
   * @return StateClass instance or empty.
   */
  private Optional<State> findStateInstanceByName(String name) {
    return stateInstances.containsKey(name)
      ? Optional.of(stateInstances.get(name))
      : Optional.empty();
  }

  /**
   * Attempts to select an on transition based on an event.
   *
   * @param event  Event to find an on transition for.
   * @param extent Extent.
   * @return On transition or empty in case no matching on transition can be selected.
   * @throws IllegalStateException If non-determinism is detected.
   */
  private Optional<Transition> trySelectOnTransition(Event event, Extent extent)
    throws IllegalStateException {
    // Find the transitions from the active state for the given event
    final var transitionObjects = stateMachineClass.findOnTransitionsFromStateByEventName(
      activeState.getStateObject(),
      event.getName()
    );

    return trySelectTransition(transitionObjects, extent);
  }

  /**
   * Attempts to select an always transition.
   *
   * @param extent Extent.
   * @return Always transition or empty in case no always transition can be selected.
   * @throws IllegalStateException If non-determinism is detected.
   */
  private Optional<Transition> trySelectAlwaysTransition(Extent extent)
    throws IllegalStateException {
    // Find the transitions from the active state for the given event
    final var transitionObjects = stateMachineClass.findAlwaysTransitionsFromState(
      activeState.getStateObject()
    );

    return trySelectTransition(transitionObjects, extent);
  }

  /**
   * Attempts to select a transition based on a collection of transition objects.
   * <p>
   * A transition is selected if it its guards evaluate to true, or if it does not, and it has an else target state.
   *
   * @param transitionObjects Collection of transition objects to select from.
   * @param extent            Extent.
   * @return Selected transition instance or empty in case no transition can be selected.
   * @throws IllegalStateException If non-determinism is detected.
   * @throws IllegalStateException If no transition could be selected.
   */
  private Optional<Transition> trySelectTransition(
    List<? extends TransitionClass> transitionObjects,
    Extent extent
  ) throws IllegalStateException {
    try {
      // A transition is taken when its guard conditions evaluate to true, or they do not evaluate to true, but an else target state is provided
      final var selectedTransitions = new ArrayList<Transition>();

      for (final var transitionObject : transitionObjects) {
        final var isElse = transitionObject.getElse().isPresent();
        final var result = transitionObject.evaluate(extent);

        if (isElse || result) {
          selectedTransitions.add(new Transition(transitionObject, isElse && !result));
        }
      }

      return switch (selectedTransitions.size()) {
        case 0 -> Optional.empty();
        case 1 -> Optional.of(selectedTransitions.getFirst());
        default -> throw new IllegalStateException("Non-determinism detected");
      };
    } catch (UnsupportedOperationException e) {
      throw new IllegalStateException("No transition could be selected", e);
    }
  }

  /**
   * Executes this collection of commands provided.
   * <p>
   * Commands are executed recursively. Any command that is the result of a command execution is executed immediately following the command
   * that created it.
   *
   * @param actionCommands Commands to execute.
   * @throws UnsupportedOperationException If the action commands cannot be executed.
   */
  private void execute(List<ActionCommand> actionCommands) throws UnsupportedOperationException {
    try {
      for (final var actionCommand : actionCommands) {
        // Execute and acquire new commands
        final var newCommands = actionCommand.execute();

        // Execute any subsequent command
        execute(newCommands);
      }
    } catch (UnsupportedOperationException e) {
      throw new UnsupportedOperationException("Could not execute action commands", e);
    }
  }

  /**
   * Starts all timeout actions as provided.
   * <p>
   * Timeout actions are executed until stopped.
   *
   * @param timeoutActionObjects Timeout action objects to start.
   * @throws UnsupportedOperationException If the delay expression does not evaluate to a numeric value.
   * @throws IllegalArgumentException      If the timeout action is not a raise action.
   * @throws IllegalArgumentException      If the timeout action does not have a name.
   */
  private void startAllTimeoutActions(List<TimeoutAction> timeoutActionObjects)
    throws UnsupportedOperationException, IllegalArgumentException {
    for (final var timeoutActionObject : timeoutActionObjects) {
      // The evaluated delay value is required to be numeric
      final var delay = timeoutActionObject.getDelay().execute(getExtent());

      if (!(delay instanceof Number)) {
        throw new UnsupportedOperationException(
          "The delay expression '%s' did not evaluate to a numeric value".formatted(
            timeoutActionObject.getDelay()
          )
        );
      }

      // Create action command
      final var actionTimeoutCommand = stateMachineScopedCommandFactory(
        this,
        null
      ).createActionCommand(timeoutActionObject.getAction());

      if (!(actionTimeoutCommand instanceof ActionRaiseCommand)) {
        throw new IllegalArgumentException("A timeout action must be a raise action");
      }

      // Acquire the name
      final var actionName = timeoutActionObject.getName();

      // Start the timeout task
      timeoutActionManager.start(actionName, (Number) delay, () -> {
        execute(List.of(actionTimeoutCommand));
      });
    }
  }

  /**
   * Stops a specific timeout action.
   *
   * @param actionName Name of timeout action to stop.
   * @throws IllegalArgumentException If not exactly one timeout action was found with the provided name.
   */
  private void stopTimeoutAction(String actionName) throws IllegalArgumentException {
    timeoutActionManager.stop(actionName);
  }

  /**
   * Stops all currently started timeout actions.
   */
  private void stopAllTimeoutActions() {
    timeoutActionManager.stopAll();
  }

  /**
   * Switches the current active state to the provided state instance.
   *
   * @param state New active state.
   * @throws IllegalArgumentException If the state is not known.
   */
  private void switchActiveState(State state) throws IllegalArgumentException {
    final var stateName = state.getStateObject().getName();

    if (!stateInstances.containsValue(state)) {
      throw new IllegalArgumentException("A state '%s' does not exist".formatted(stateName));
    }

    // Update the active state
    activeState = state;
  }

  /**
   * Performs exiting a state.
   * <p>
   * The order of execution is as follows: stop all currently started timeout actions, cancel currently running while actions, execute exit
   * actions.
   *
   * @param exitingState StateClass instance that is being exited.
   * @param raisingEvent The raising event or null.
   * @throws UnsupportedOperationException If the exit action cannot be executed.
   */
  private void doExit(State exitingState, @Nullable Event raisingEvent)
    throws UnsupportedOperationException {
    // Gather action commands
    final var exitActionCommands = exitingState.getExitActionCommands(
      stateScopedCommandFactory(exitingState, raisingEvent, false)
    );

    // Stop timeout actions
    stopAllTimeoutActions();

    // TODO: Cancel while actions

    // Execute in order
    try {
      execute(exitActionCommands);
    } catch (UnsupportedOperationException e) {
      throw new UnsupportedOperationException("Could not execute exit actions", e);
    }
  }

  /**
   * Performs transitioning.
   * <p>
   * The order of execution is as follows: execute transition commands.
   *
   * @param transition   TransitionClass instance that is being taken.
   * @param raisingEvent The raising event or null.
   * @throws UnsupportedOperationException If the transition action cannot be executed.
   */
  private void doTransition(Transition transition, @Nullable Event raisingEvent)
    throws UnsupportedOperationException {
    // Do not execute actions for else target transitions
    if (transition.isElse()) {
      return;
    }

    // Gather action commands
    final var transitionActionCommands = transition.getActionCommands(
      stateMachineScopedCommandFactory(this, raisingEvent)
    );

    // Execute in order
    try {
      execute(transitionActionCommands);
    } catch (UnsupportedOperationException e) {
      throw new UnsupportedOperationException("Could not execute transition actions", e);
    }
  }

  /**
   * Performs entering a state.
   * <p>
   * The order of execution is as follows: execute entry actions, execute while actions, start timeout actions, switch to state.
   * <p>
   * Any subsequent always actions are selected at the end.
   *
   * @param enteringState StateClass instance that is being entered.
   * @param raisingEvent  The raising event or null.
   * @throws UnsupportedOperationException If the entry/while actions could not be executed.
   * @throws UnsupportedOperationException If the timeout actions could not be started.
   * @throws UnsupportedOperationException If the states could not be switched.
   * @throws UnsupportedOperationException If an always transition could not be selected.
   */
  private Optional<Transition> doEnter(State enteringState, @Nullable Event raisingEvent)
    throws UnsupportedOperationException, IllegalArgumentException {
    // Gather action commands
    final var entryActionCommands = enteringState.getEntryActionCommands(
      stateScopedCommandFactory(enteringState, raisingEvent, false)
    );

    final var whileActionCommands = enteringState.getWhileActionCommands(
      stateScopedCommandFactory(enteringState, raisingEvent, true)
    );

    final var timeoutActionObjects = enteringState.getTimeoutActionObjects();

    // Execute in order
    try {
      execute(entryActionCommands);
      execute(whileActionCommands);
    } catch (UnsupportedOperationException e) {
      throw new UnsupportedOperationException("Could not execute entry/while actions", e);
    }

    // Start timeout actions
    try {
      startAllTimeoutActions(timeoutActionObjects);
    } catch (UnsupportedOperationException | IllegalArgumentException e) {
      throw new UnsupportedOperationException("Could not start timeout actions", e);
    }

    // Switch the active state to the entering state
    try {
      switchActiveState(enteringState);
    } catch (IllegalArgumentException e) {
      throw new UnsupportedOperationException("Could not switch states");
    }

    try {
      return trySelectAlwaysTransition(getExtent());
    } catch (IllegalStateException e) {
      throw new UnsupportedOperationException("Could not select always transition");
    }
  }

  /**
   * Handles an internal transition.
   *
   * @param transition   TransitionClass instance.
   * @param raisingEvent The raising event or null.
   * @throws UnsupportedOperationException If the transition could not be handled.
   */
  private void handleInternalTransition(
    @NotNull Transition transition,
    @Nullable Event raisingEvent
  ) throws UnsupportedOperationException {
    // Only perform the transition
    doTransition(transition, raisingEvent);
  }

  /**
   * Handles an external transition.
   *
   * @param transition   TransitionClass instance.
   * @param raisingEvent The raising event or null.
   * @throws UnsupportedOperationException If the transition could not be handled.
   */
  private void handleExternalTransition(
    @NotNull Transition transition,
    @Nullable Event raisingEvent
  ) throws UnsupportedOperationException {
    final var targetStateName = transition.getTargetStateName().get();

    // Acquire the target state instance
    final var targetStateInstance = findStateInstanceByName(targetStateName).orElseThrow(() ->
      new IllegalArgumentException(
        "Target state '%s' cannot be found in state machine".formatted(
          transition.getTargetStateName()
        )
      )
    );

    // Exit the current state
    doExit(activeState, raisingEvent);

    // Perform the transition
    doTransition(transition, raisingEvent);

    // Enter the target state, if there is a follow-up transition, handle it recursively
    final var nextTransitionInstance = doEnter(targetStateInstance, raisingEvent);

    nextTransitionInstance.ifPresent(t -> handleTransition(t, raisingEvent));
  }

  /**
   * Handles a transition.
   *
   * @param transition   TransitionClass instance.
   * @param raisingEvent The raising event or null.
   * @throws UnsupportedOperationException If the transition could not be handled.
   */
  private void handleTransition(@NotNull Transition transition, @Nullable Event raisingEvent)
    throws UnsupportedOperationException {
    if (transition.isInternalTransition()) {
      handleInternalTransition(transition, raisingEvent);
    } else {
      handleExternalTransition(transition, raisingEvent);
    }
  }

  /**
   * Handles an event.
   * <p>
   * This function blocks until a new event is received.
   *
   * @throws InterruptedException          If interrupted while waiting for an event.
   * @throws UnsupportedOperationException If an on transition could not be selected.
   */
  private Optional<Transition> handleEvent(Event event)
    throws InterruptedException, UnsupportedOperationException {
    // Increment events received counter
    counters
      .getCounter(COUNTER_EVENTS_HANDLED)
      .add(1, counters.attributesForEvent(event.getChannel().toString()));

    // Find a matching transition
    try {
      // Create a temporary in-memory context containing the event data
      final var eventDataContext = new InMemoryContext(true);

      for (var contextVariable : event.getData()) {
        eventDataContext.create(
          EVENT_DATA_VARIABLE_PREFIX + contextVariable.name(),
          contextVariable.value()
        );
      }

      // Create a temporary extent that contains the event data
      final var extent = getExtent().extend(eventDataContext);

      final var onTransition = trySelectOnTransition(event, extent);

      // Set the event data in the actual extent
      onTransition.ifPresent(transition -> {
        try {
          for (var contextVariable : event.getData()) {
            getExtent().setOrCreate(
              EVENT_DATA_VARIABLE_PREFIX + contextVariable.name(),
              contextVariable.value()
            );
          }
        } catch (IOException e) {
          logger.error("Failed to set event data", e);
        }
      });

      return onTransition;
    } catch (IOException | IllegalStateException e) {
      throw new UnsupportedOperationException("Could not select on transition", e);
    }
  }

  /**
   * Runs this state machine instance.
   * <p>
   * Execution ends when the state machine instance has reached a terminal state or when it is interrupted.
   */
  @Override
  public void run() {
    // Increment state machine instances counter
    counters.getCounter(COUNTER_STATE_MACHINE_INSTANCES).add(1, counters.attributesForInstances());

    try {
      // Acquire the initial state instance
      final var initialStateInstance = stateInstances.get(
        stateMachineClass.getInitialState().getName()
      );

      // TransitionClass into the initial state
      var nextTransition = doEnter(initialStateInstance, null);

      while (!isTerminated()) {
        Event event = null;

        // Wait for a next event, if no transition is selected. No transition is selected initially if the initial state has no selectable
        // always transition or thereafter if we've handled the selected transition
        if (nextTransition.isEmpty()) {
          synchronized (this) {
            while (eventQueue.isEmpty()) {
              wait();
            }
            event = eventQueue.poll();
          }

          nextTransition = handleEvent(event);
        }

        // If a transition is selected, handle it. The transition will be handled recursively; any transition selected due to entering a
        // next state is handled recursively. Therefore, if we're done handling this transition, we indicate that we need to wait for a new
        // event again
        if (nextTransition.isPresent()) {
          handleTransition(nextTransition.get(), event);

          nextTransition = Optional.empty();
        }

        // Record event handling time
        if (event != null) {
          final var delta = Time.timeInMillisecondsSinceEpoch() - event.getCreatedTime();

          gauges
            .getGauge(GAUGE_EVENT_RESPONSE_TIME_EXCLUSIVE)
            .set(delta, gauges.attributesForEvent(event.getChannel().toString()));
        }
      }
    } catch (InterruptedException e) {
      logger.info("{} is interrupted", stateMachineId.toString());

      Thread.currentThread().interrupt();
    } catch (Exception e) {
      logger.error("%s received a fatal error".formatted(stateMachineId.toString()), e);
    }

    logger.info("{} has stopped", stateMachineId.toString());

    // Decrement state machine instances counter
    counters.getCounter(COUNTER_STATE_MACHINE_INSTANCES).add(-1, counters.attributesForInstances());

    // Remove the state machine instance from the runtime
    parentRuntime.remove(this);
  }

  /**
   * Returns this scope's extent.
   *
   * @return Extent.
   */
  @Override
  public Extent getExtent() {
    return Optional.ofNullable(parentStateMachine)
      .map(parent -> parent.getExtent().extend(localContext))
      .orElseGet(() -> parentRuntime.getExtent().extend(localContext));
  }

  @Override
  public String getId() {
    return getStateMachineInstanceId().toString();
  }

  /**
   * Returns the state machine instance ID.
   *
   * @return StateClass machine instance ID.
   */
  public Id getStateMachineInstanceId() {
    return stateMachineId;
  }

  /**
   * Returns the state machine object.
   *
   * @return StateClass machine object.
   */
  public StateMachineClass getStateMachineClass() {
    return stateMachineClass;
  }

  /**
   * Sets the collection of nested state machine instance IDs.
   *
   * @param nestedStateMachineIds Nested state machine instance IDs.
   */
  public void setNestedStateMachineIds(List<Id> nestedStateMachineIds) {
    this.nestedStateMachineIds = nestedStateMachineIds;
  }
}
