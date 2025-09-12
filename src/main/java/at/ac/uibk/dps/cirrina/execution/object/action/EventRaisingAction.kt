package at.ac.uibk.dps.cirrina.execution.`object`.action

import at.ac.uibk.dps.cirrina.execution.`object`.event.Event

interface EventRaisingAction {
  fun raises(): List<Event>
}
