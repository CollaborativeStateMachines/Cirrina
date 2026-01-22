package at.ac.uibk.dps.cirrina.execution.command

import at.ac.uibk.dps.cirrina.execution.`object`.context.Extent

/**
 * A representation of an execution scope containing data and state information.
 *
 * This scope provides access to the current [Extent].
 */
interface Scope {

  /** The current extent providing access to scoped variables and their values. */
  val extent: Extent
}
