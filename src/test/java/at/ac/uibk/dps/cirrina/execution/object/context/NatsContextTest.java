package at.ac.uibk.dps.cirrina.execution.object.context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Assumptions;

public class NatsContextTest extends ContextTest {

  @Override
  protected Context createContext() {
    String natsServerURL = System.getenv("NATS_SERVER_URL");

    Assumptions.assumeFalse(natsServerURL == null, "Skipping NATS persistent context test");

    return assertDoesNotThrow(() -> new NatsContext(true, natsServerURL, "persistent"));
  }
}
