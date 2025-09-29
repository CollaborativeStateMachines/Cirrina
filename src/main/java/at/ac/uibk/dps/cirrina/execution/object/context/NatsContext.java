package at.ac.uibk.dps.cirrina.execution.object.context;

import at.ac.uibk.dps.cirrina.execution.object.exchange.ValueExchange;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.Nats;
import io.nats.client.api.KeyValueConfiguration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A persistent context containing within a NATS bucket.
 */
public final class NatsContext extends Context implements AutoCloseable {

  /**
   * The NATS context logger.
   */
  private static final Logger logger = LogManager.getLogger();

  /**
   * The NATS connection.
   */
  private final Connection connection;

  /**
   * The key-value.
   */
  private final KeyValue keyValue;

  /**
   * Initializes an empty persistent context.
   *
   * @param isLocal True if this context is local, otherwise false.
   * @param natsUrl NATS server URL.
   * @throws IOException If a connection could not be made to the NATS server.
   */
  public NatsContext(boolean isLocal, String natsUrl, String bucketName) throws IOException {
    super(isLocal);
    // Attempt to connect to the NATS server
    try {
      connection = Nats.connect(natsUrl);
    } catch (InterruptedException | IOException e) {
      throw new IOException("Could not connect to the NATS server", e);
    }

    // Attempt to retrieve the bucket, which is expected to be pre-created. We do not manage the creation/deletion of
    // buckets
    try {
      var keyValueManagement = connection.keyValueManagement();

      // Bucket should not exist yet
      if (!keyValueManagement.getBucketNames().contains(bucketName)) {
        logger.warn("A bucket with the name '{}' does not exists, creating the bucket", bucketName);

        // Create the bucket
        keyValueManagement.create(new KeyValueConfiguration.Builder().name(bucketName).build());
      }

      // Retrieve the bucket
      keyValue = connection.keyValue(bucketName);
    } catch (IOException | JetStreamApiException e) {
      throw new IOException(
        "Failed to create the persistent context bucket: %s".formatted(e.getMessage())
      );
    }
  }

  /**
   * Retrieve a context variable.
   * <p>
   * A variable with this name should be created prior.
   *
   * @param name Name of the context variable.
   * @return The retrieved context variable.
   * @throws IOException If a variable with the same does not exist.
   * @throws IOException If the context variable could not be retrieved.
   */
  @Override
  public Object get(String name) throws IOException {
    try {
      if (!keyValue.keys().contains(name)) {
        throw new IOException("A variable with the name '%s' does not exist".formatted(name));
      }

      var entry = keyValue.get(name);

      return fromBytes(entry.getValue());
    } catch (
      IOException
      | JetStreamApiException
      | UnsupportedOperationException
      | InterruptedException e
    ) {
      throw new IOException("Failed to retrieve the variable '%s'".formatted(name), e);
    }
  }

  /**
   * Creates a context variable.
   * <p>
   * A variables should not yet be created with the same name.
   *
   * @param name  Name of the context variable.
   * @param value Value of the context variable.
   * @return Byte size of stored data.
   * @throws IOException If a variable with the same name already exists.
   * @throws IOException If the variable could not be created.
   */
  @Override
  public int create(String name, Object value) throws IOException {
    try {
      if (keyValue.keys().contains(name)) {
        throw new IOException("A variable with the name '%s' already exists".formatted(name));
      }

      final var data = toBytes(value);
      keyValue.create(name, data);

      return data.length;
    } catch (
      IOException
      | JetStreamApiException
      | UnsupportedOperationException
      | InterruptedException e
    ) {
      throw new IOException("Failed to create variable '%s'".formatted(name), e);
    }
  }

  /**
   * Assigns to a context variable.
   *
   * @param name  Name of the context variable.
   * @param value New value of the context variable.
   * @return Byte size of stored data.
   * @throws IOException If a variable with the same does not exist.
   * @throws IOException If the variable could not be assigned to.
   */
  @Override
  public int assign(String name, Object value) throws IOException {
    try {
      if (!keyValue.keys().contains(name)) {
        throw new IOException("A variable with the name '%s' does not exist".formatted(name));
      }

      final var data = toBytes(value);
      keyValue.put(name, data);

      return data.length;
    } catch (IOException | JetStreamApiException | InterruptedException e) {
      throw new IOException("Failed to assign to the variable '%s'".formatted(name), e);
    }
  }

  /**
   * Deletes a context variable.
   *
   * @param name Name of the context variable.
   * @throws IOException If a variable with the same does not exist.
   * @throws IOException If the variable could not be deleted.
   */
  @Override
  public void delete(String name) throws IOException {
    try {
      if (!keyValue.keys().contains(name)) {
        throw new IOException("A variable with the name '%s' does not exist".formatted(name));
      }

      keyValue.delete(name);
    } catch (IOException | JetStreamApiException | InterruptedException e) {
      throw new IOException("Failed to delete the variable '%s'".formatted(name), e);
    }
  }

  /**
   * Returns all context variables.
   *
   * @return Context variables.
   * @throws IOException If the variables could not be retrieved.
   */
  @Override
  public List<ContextVariable> getAll() throws IOException {
    var ret = new ArrayList<ContextVariable>();

    try {
      for (var key : keyValue.keys()) {
        var entry = keyValue.get(key);
        ret.add(new ContextVariable(entry.getKey(), entry.getValue()));
      }
    } catch (IOException | JetStreamApiException | InterruptedException e) {
      throw new IOException("Failed to retrieve variables from context", e);
    }

    return ret;
  }

  private byte[] toBytes(Object value) throws UnsupportedOperationException {
    return new ValueExchange(value).toBytes();
  }

  private Object fromBytes(byte[] bytes) throws UnsupportedOperationException {
    return ValueExchange.fromBytes(bytes).getValue();
  }

  @Override
  public void close() throws IOException {
    try {
      var keyValueManagement = connection.keyValueManagement();

      // Delete the bucket
      keyValueManagement.delete(keyValue.getBucketName());

      connection.close();
    } catch (IOException | JetStreamApiException | InterruptedException e) {
      throw new IOException("Failed to close NATS persistent context", e);
    }
  }
}
