package at.ac.uibk.dps.cirrina.execution.object.context;

import at.ac.uibk.dps.cirrina.csml.description.CollaborativeStateMachineDescription.ContextDescription;
import at.ac.uibk.dps.cirrina.execution.object.expression.ExpressionBuilder;
import jakarta.annotation.Nullable;
import java.io.IOException;

/**
 * Context builder, builder for various context implementations.
 */
public class ContextBuilder {

  private final @Nullable ContextDescription contextClass;

  private Context context;

  private Extent extent;

  /**
   * Initializes this context builder object.
   */
  private ContextBuilder() {
    this.contextClass = null;
  }

  /**
   * Initializes this context builder object.
   *
   * @param contextDescription Context class.
   */
  private ContextBuilder(ContextDescription contextDescription) {
    this.contextClass = contextDescription;
  }

  /**
   * Construct a builder from nothing.
   *
   * @return Context builder.
   */
  public static ContextBuilder from() {
    return new ContextBuilder();
  }

  /**
   * Construct a builder from a context class.
   *
   * @param contextDescription Context class.
   * @return Context builder.
   */
  public static ContextBuilder from(ContextDescription contextDescription) {
    return new ContextBuilder(contextDescription);
  }

  /**
   * Build an in-memory context.
   *
   * @param isLocal True if this context is local, otherwise false.
   * @return This builder.
   */
  public ContextBuilder inMemoryContext(boolean isLocal) {
    context = new InMemoryContext(isLocal);

    return this;
  }

  /**
   * Build a NATS context.
   *
   * @param isLocal    True if this context is local, otherwise false.
   * @param natsUrl    NATS url.
   * @param bucketName NATS bucket name.
   * @return This builder.
   * @throws IOException If the context could not be built.
   * @see NatsContext
   */
  public ContextBuilder natsContext(boolean isLocal, String natsUrl, String bucketName)
    throws IOException {
    context = new NatsContext(isLocal, natsUrl, bucketName);

    return this;
  }

  public ContextBuilder withExtent(Extent extent) {
    this.extent = extent;
    return this;
  }

  /**
   * Builds the current context.
   *
   * @return Context.
   * @throws IOException If the context could not be built.
   */
  public Context build() throws IOException {
    assert context != null;

    // Add all variables contained within the context class to the newly created context, only do this if there is a class
    if (contextClass != null) {
      for (var contextVariable : contextClass.getVariables()) {
        // Build the value expression
        var expression = ExpressionBuilder.from(contextVariable.getValue()).build();

        // Acquire the variable name
        var name = contextVariable.getName();

        // Acquire the variable value
        // We pass the provided extent which will often be null
        // It may not be null, if this new context belongs to a state machine that gets created by a SpawnAction command
        var value = expression.execute(extent);

        // Create the variable
        context.create(name, value);
      }
    }

    return context;
  }
}
