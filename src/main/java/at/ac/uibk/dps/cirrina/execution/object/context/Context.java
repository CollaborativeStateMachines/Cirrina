package at.ac.uibk.dps.cirrina.execution.object.context;

import java.io.IOException;
import java.util.List;

/**
 * Base context, containing context variables.
 */
public abstract class Context implements AutoCloseable {

  private final boolean isLocal;

  /**
   * Initializes this context object.
   *
   * @param isLocal true if this context is local, otherwise false
   */
  public Context(boolean isLocal) {
    this.isLocal = isLocal;
  }

  /**
   * Retrieve a context variable.
   *
   * @param name name of the context variable
   * @return The retrieved context variable
   * @throws IOException if the context variable could not be retrieved
   */
  public abstract Object get(String name) throws IOException;

  /**
   * Creates a context variable.
   *
   * @param name  name of the context variable
   * @param value value of the context variable
   * @return byte size of stored data
   * @throws IOException if the variable could not be created
   */
  public abstract int create(String name, Object value) throws IOException;

  /**
   * Assigns to a context variable.
   *
   * @param name  name of the context variable
   * @param value new value of the context variable
   * @return byte size of stored data
   * @throws IOException if the variable could not be assigned to
   */
  public abstract int assign(String name, Object value) throws IOException;

  /**
   * Deletes a context variable.
   *
   * @param name Name of the context variable.
   * @throws IOException If the variable could not be deleted.
   */
  public abstract void delete(String name) throws IOException;

  /**
   * Deletes all context variables.
   *
   * @throws IOException if the variable could not be deleted
   */
  public abstract void deleteAll() throws IOException;

  /**
   * Returns all context variables.
   *
   * @return context variables.
   * @throws IOException if the variables could not be retrieved
   */
  public abstract List<ContextVariable> getAll() throws IOException;

  /**
   * Returns a flag that indicates if this context is local.
   *
   * @return true if local, otherwise false
   */
  public boolean isLocal() {
    return isLocal;
  }
}
