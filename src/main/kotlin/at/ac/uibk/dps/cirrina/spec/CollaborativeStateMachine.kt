package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.CollaborativeStateMachineDescription

class CollaborativeStateMachine
private constructor(csml: Csml, description: CollaborativeStateMachineDescription) {
  /** The list of persistent context variables. */
  val persistentContext: Map<String, String>? = description.persistent

  /** The map of state machine specifications. */
  val stateMachines =
    description.stateMachines.mapValues { (name, desc) ->
      StateMachine.create(csml, desc, name).getOrThrow()
    }

  fun getStateMachine(name: String) =
    stateMachines[name] ?: error("state machine class '${name}' not found")

  fun getAllActions(): List<Action> {
    fun StateMachine.allStateMachines(): List<StateMachine> =
      listOf(this) + nested.flatMap { it.allStateMachines() }

    return stateMachines.values
      .flatMap { it.allStateMachines() }
      .flatMap { sm ->
        val stateActions =
          sm.vertexSet().flatMap { state ->
            listOf(state.entry, state.exit, state.during, state.after).flatMap { it.vertexSet() }
          }

        val transitionActions =
          sm.edgeSet().flatMap { transition -> transition.actions.vertexSet() }

        stateActions + transitionActions
      }
  }

  companion object {
    fun create(csml: Csml, description: CollaborativeStateMachineDescription) = runCatching {
      CollaborativeStateMachine(csml, description)
    }
  }
}
