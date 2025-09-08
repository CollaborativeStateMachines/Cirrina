package at.ac.uibk.dps.cirrina.data;

import org.junit.jupiter.api.Test;

public class DefaultDescriptions {

  public static final String complete = "pkl/complete/main.pkl";

  public static final String completeNested = "pkl/completeNested/main.pkl";

  public static final String invoke = "pkl/invoke/main.pkl";

  public static final String timeout = "pkl/timeout/main.pkl";

  public static final String pingPong = "pkl/pingPong/main.pkl";

  @Test
  void testLoadResource() {
    assert !complete.isEmpty();
    assert !completeNested.isEmpty();
    assert !invoke.isEmpty();
    assert !timeout.isEmpty();
    assert !pingPong.isEmpty();
  }
}
