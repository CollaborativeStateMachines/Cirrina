package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

interface EventRaisingAction : Action {

  fun raises(): List<Event>
}
