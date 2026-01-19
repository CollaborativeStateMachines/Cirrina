package at.ac.uibk.dps.cirrina.execution.`object`.event

interface EventListener {
  fun onReceiveEvent(event: Event): Boolean
}
