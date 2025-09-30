package at.ac.uibk.dps.cirrina.execution.`object`.context

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.assertDoesNotThrow

class NatsContextTest : ContextTest() {
  override fun createContext(): Context {
    val natsServerURL = System.getenv("NATS_SERVER_URL")

    assumeFalse(natsServerURL == null, "Skipping NATS persistent context test")

    return assertDoesNotThrow {
      NatsContext(true, natsServerURL, "test", true).apply { awaitInitialConnection() }
    }
  }
}
