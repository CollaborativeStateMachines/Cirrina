package at.ac.uibk.dps.cirrina.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class DefaultDescriptions {

  public static final String empty = "";

  public static final String complete = ""; // TODO: Fix me

  public static final String completeNested = ""; // TODO: Fix me

  public static final String invoke = loadResource("invoke.pkl");

  public static final String timeout = loadResource("timeout.pkl");

  public static final String pingPong = loadResource("pingPong.pkl");

  private static String loadResource(String fileName) {
    try (
      InputStream inputStream = DefaultDescriptions.class.getResourceAsStream("/pkl/" + fileName)
    ) {
      if (inputStream == null) {
        throw new IOException("Resource not found: " + fileName);
      }
      byte[] bytes = inputStream.readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load resource: " + fileName, e);
    }
  }

  @Test
  void testLoadResource() {
    assert !empty.isEmpty();
    assert !complete.isEmpty();
    assert !completeNested.isEmpty();
    assert !invoke.isEmpty();
    assert !timeout.isEmpty();
    assert !pingPong.isEmpty();
  }
}
