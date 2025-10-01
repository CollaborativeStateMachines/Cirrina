package at.ac.uibk.dps.cirrina.data

import at.ac.uibk.dps.cirrina.utils.TestUtils.resourceUri
import java.net.URI

object DefaultDescriptions {
  val complete: URI by lazy { resourceUri("pkl/complete/main.pkl") }
  val invoke: URI by lazy { resourceUri("pkl/invoke/main.pkl") }
  val timeout: URI by lazy { resourceUri("pkl/timeout/main.pkl") }
  val pingPong: URI by lazy { resourceUri("pkl/pingPong/main.pkl") }
  val noop: URI by lazy { resourceUri("pkl/noop/main.pkl") }
  val empty: URI by lazy { resourceUri("pkl/noop/empty.pkl") }
}
