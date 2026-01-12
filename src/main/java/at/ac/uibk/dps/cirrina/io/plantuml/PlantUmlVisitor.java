package at.ac.uibk.dps.cirrina.io.plantuml;

import at.ac.uibk.dps.cirrina.classes.state.StateClass;
import at.ac.uibk.dps.cirrina.classes.statemachine.StateMachineClass;
import at.ac.uibk.dps.cirrina.classes.transition.OnTransitionClass;
import at.ac.uibk.dps.cirrina.classes.transition.TransitionClass;
import at.ac.uibk.dps.cirrina.execution.object.action.Action;
import at.ac.uibk.dps.cirrina.execution.object.action.AssignAction;
import at.ac.uibk.dps.cirrina.execution.object.action.InvokeAction;
import at.ac.uibk.dps.cirrina.execution.object.action.MatchAction;
import at.ac.uibk.dps.cirrina.execution.object.action.RaiseAction;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutAction;
import at.ac.uibk.dps.cirrina.execution.object.action.TimeoutResetAction;
import java.util.Objects;
import java.util.Optional;

public class PlantUmlVisitor {

  private final StringBuilder plantUml = new StringBuilder();
  private StringBuilder actionBuilder = new StringBuilder();
  private StringBuilder transitionBuilder = new StringBuilder();

  public String getPlantUml() {
    if (plantUml.isEmpty()) {
      throw new IllegalStateException("No plantUml generated, visit a state machine first.");
    }
    return plantUml.toString();
  }

  public void visit(StateMachineClass stateMachineClass) {
    plantUml.append("state ").append(stateMachineClass.getName()).append(" {\n");

    stateMachineClass
      .vertexSet()
      .forEach(s -> {
        plantUml.append("    ");
        s.accept(this);
        plantUml.append("\n");
      });
    stateMachineClass
      .edgeSet()
      .forEach(t -> {
        plantUml.append("    ");
        t.accept(this);
        plantUml.append(transitionBuilder);
        plantUml.append("\n");
      });

    stateMachineClass
      .getNestedStateMachineClasses()
      .forEach(sm -> {
        sm.accept(this);
        plantUml.append("\n");
      });

    plantUml.append("}\n");
  }

  public void visit(StateClass stateClass) {
    plantUml.append(
      String.format("state \"%s\" as %s%n    ", stateClass.getName(), getStateId(stateClass))
    );

    if (stateClass.isInitial()) {
      plantUml.append(String.format("[*] --> %s%n    ", getStateId(stateClass)));
    }
    if (stateClass.isTerminal()) {
      plantUml.append(String.format("%s --> [*]%n    ", getStateId(stateClass)));
    }
    if (!stateClass.getEntryActionGraph().getActions().isEmpty()) {
      actionBuilder = new StringBuilder();
      stateClass
        .getEntryActionGraph()
        .getActions()
        .forEach(action -> {
          action.accept(this);
          actionBuilder.append("; ");
        });
      plantUml.append(
        String.format(
          "state \"%s\" as %s : entry / %s%n    ",
          stateClass.getName(),
          getStateId(stateClass),
          actionBuilder
        )
      );
    }

    if (!stateClass.getExitActionGraph().getActions().isEmpty()) {
      actionBuilder = new StringBuilder();
      stateClass
        .getExitActionGraph()
        .getActions()
        .forEach(action -> {
          action.accept(this);
          actionBuilder.append("; ");
        });
      plantUml.append(
        String.format(
          "state \"%s\" as %s : exit / %s%n    ",
          stateClass.getName(),
          getStateId(stateClass),
          actionBuilder
        )
      );
    }

    if (!stateClass.getWhileActionGraph().getActions().isEmpty()) {
      actionBuilder = new StringBuilder();
      var whileActions = stateClass.getWhileActionGraph().getActions();
      whileActions.forEach(action -> {
        action.accept(this);
        actionBuilder.append("; ");
      });
      plantUml.append(
        String.format(
          "state \"%s\" as %s : while / %s%n    ",
          stateClass.getName(),
          getStateId(stateClass),
          actionBuilder
        )
      );
    }
  }

  public void visit(Action action) {
    switch (action) {
      case AssignAction a -> actionBuilder.append(
        String.format(
          "<color:%s>%s{%s = %s}()</color>",
          ActionColors.getActionColor(a),
          "Assign",
          a.getVariable().name(),
          a.getVariable().value()
        )
      );
      case InvokeAction a -> actionBuilder.append(
        String.format(
          "<color:%s>%s{%s}(%s)</color>",
          ActionColors.getActionColor(a),
          "Invoke",
          a.getServiceType(),
          a.getInput()
        )
      );
      case MatchAction a -> actionBuilder.append(
        String.format("<color:%s>%s()</color>", ActionColors.getActionColor(a), "Match")
      );
      case RaiseAction a -> actionBuilder.append(
        String.format(
          "<color:%s>%s{%s}()</color>",
          ActionColors.getActionColor(a),
          "Raise",
          a.getEvent()
        )
      );
      case TimeoutAction a -> actionBuilder.append(
        String.format(
          "<color:%s>%s()</color>",
          ActionColors.getActionColor(a),
          "Timeout{%s}".formatted(a.getName())
        )
      );
      case TimeoutResetAction a -> actionBuilder.append(
        String.format("<color:%s>%s()</color>", ActionColors.getActionColor(a), "TimeoutReset")
      );
      default -> throw new IllegalStateException("Unexpected action");
    }
  }

  public void visit(TransitionClass transitionClass) {
    transitionBuilder = new StringBuilder();
    if (Objects.requireNonNull(transitionClass) instanceof OnTransitionClass t) {
      transitionBuilder.append(buildPlantUml(t, Optional.of(t.getEventName())));
    } else {
      transitionBuilder.append(buildPlantUml(transitionClass, Optional.empty()));
    }
  }

  protected StringBuilder buildPlantUml(
    TransitionClass transitionClass,
    Optional<String> eventName
  ) {
    var descriptionBuilder = new StringBuilder();

    var guardBuilder = new StringBuilder();
    var guardList = transitionClass.getGuards();
    if (!guardList.isEmpty()) {
      for (int i = 0; i < guardList.size() - 1; i++) {
        guardBuilder.append(guardList.get(i).getExpression().toString()).append(" && ");
      }
      guardBuilder.append(guardList.getLast().getExpression().toString());
    }

    actionBuilder = new StringBuilder();
    if (!transitionClass.getActionGraph().getActions().isEmpty()) {
      actionBuilder.append("\\n/ ");
      transitionClass
        .getActionGraph()
        .getActions()
        .forEach(action -> {
          visit(action);
          actionBuilder.append("; ");
        });
    }

    var eventNameBuilder = new StringBuilder();
    var isOnTransition = eventName.isPresent();
    if (isOnTransition) {
      eventNameBuilder.append("**").append(eventName.get()).append("**");
    }

    var sourceStateId = getStateId(transitionClass.getSource());
    var targetStateId = getStateId(transitionClass.getTarget());

    descriptionBuilder
      .append(sourceStateId)
      .append(getArrow(transitionClass))
      .append(targetStateId);
    if (!guardBuilder.isEmpty()) {
      descriptionBuilder
        .append(" : ")
        .append(eventNameBuilder)
        .append("\\n[")
        .append(guardBuilder)
        .append("]")
        .append(actionBuilder);
    } else if (!eventNameBuilder.isEmpty() || !actionBuilder.isEmpty()) {
      descriptionBuilder.append(" : ").append(eventNameBuilder).append(actionBuilder);
    }

    return descriptionBuilder;
  }

  private String getArrow(TransitionClass transitionClass) {
    boolean isInternal = transitionClass.getTargetStateName().isEmpty();

    if (isInternal) {
      return " -[dashed]-> ";
    } else {
      return " --> ";
    }
  }

  private String getStateId(StateClass stateClass) {
    return (
      stateClass.getParentStateMachineClassId().toString().replace("-", "_") +
      "_" +
      stateClass.getName()
    );
  }
}
