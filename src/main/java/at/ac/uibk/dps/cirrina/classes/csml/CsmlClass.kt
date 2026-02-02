package at.ac.uibk.dps.cirrina.classes.csml

import at.ac.uibk.dps.cirrina.classes.collaborativestatemachine.CollaborativeStateMachineClass
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable

/** Represents the structure of a CSML application. */
class CsmlClass(
  val collaborativeStateMachineClass: CollaborativeStateMachineClass,
  val instantiate: Map<String, String>,
  val instanceData: Map<String, List<ContextVariable>>,
  val instanceSubscriptions: Map<String, List<String>>,
)
