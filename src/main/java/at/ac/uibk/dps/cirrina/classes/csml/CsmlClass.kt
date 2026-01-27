package at.ac.uibk.dps.cirrina.classes.csml

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass

/** Represents the structure of a CSML application. */
class CsmlClass(
  val collaborativeStateMachineClass: CollaborativeStateMachineClass,
  val instantiate: Map<String, String>,
)
