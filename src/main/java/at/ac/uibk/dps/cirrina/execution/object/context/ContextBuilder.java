package at.ac.uibk.dps.cirrina.execution.object.context;

import at.ac.uibk.dps.cirrina.execution.object.expression.ExpressionBuilder;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

/**
 * Context builder, builder for various context implementations.
 */
public class ContextBuilder {

  private final @Nullable Map<String, String> contextDescription;

  private Context context;

  /**
   * Initializes this builder object.
   */
  private ContextBuilder() {
    this.contextDescription = null;
  }

  /**
   * Initializes this builder object.
   *
   * @param contextDescription context class
   */
  private ContextBuilder(Map<String, String> contextDescription) {
    this.contextDescription = contextDescription;
  }

  /**
   * Construct a builder from nothing.
   *
   * @return this builder
   */
  public static ContextBuilder empty() {
    return new ContextBuilder();
  }

  /**
   * Construct a builder from a context class.
   *
   * @param contextDescription context description
   * @return this builder
   */
  public static ContextBuilder from(Map<String, String> contextDescription) {
    return new ContextBuilder(contextDescription);
  }

  /**
   * Build an in-memory context.
   *
   * @param isLocal true if this context is local, otherwise false
   * @return this builder
   */
  public ContextBuilder inMemoryContext(boolean isLocal) {
    context = new InMemoryContext(isLocal);

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
    if (contextDescription != null) {
      for (var varEntry : contextDescription.entrySet()) {
        // Build the value expression
        var expression = ExpressionBuilder.from(varEntry.getValue()).build();

        // Acquire the variable name
        var name = varEntry.getKey();

        // Acquire the variable value
        // We pass an empty extent here, I don't think that it makes too much sense to provide anything other than an empty extent here,
        // because I currently don't see a use case for looking up variables in scope while constructing a context
        var value = expression.execute(new Extent());

        // Create the variable
        context.create(name, value);
      }
    }

    return context;
  }
}
