package at.ac.uibk.dps.cirrina.execution.service;

import at.ac.uibk.dps.cirrina.execution.object.context.ContextVariable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract service implementation, needs to be specialized.
 *
 * @see HttpServiceImplementation
 */
public abstract class ServiceImplementation {

  private final String name;
  private final boolean local;

  /**
   * Initializes this service implementation.
   *
   * @param name  Name of this service implementation.
   * @param local Whether this service implementation is local.
   */
  public ServiceImplementation(String name, boolean local) {
    this.name = name;
    this.local = local;
  }

  /**
   * Invoke this service implementation.
   *
   * @param input Input to the service invocation.
   * @param id    Sender ID.
   * @return The service invocation output.
   * @throws IOException If the service invocation failed.
   */
  public abstract CompletableFuture<List<ContextVariable>> invoke(
    List<ContextVariable> input,
    String id
  ) throws IOException;

  /**
   * Returns whether this service implementation is local.
   *
   * @return Is local.
   */
  public boolean isLocal() {
    return local;
  }

  /**
   * Returns the name of this service implementation.
   *
   * @return Name.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns a string for informative purposes (a service implementation is abstract, so can provide no information about the details).
   *
   * @return Information string.
   */
  public abstract String getInformationString();
}
