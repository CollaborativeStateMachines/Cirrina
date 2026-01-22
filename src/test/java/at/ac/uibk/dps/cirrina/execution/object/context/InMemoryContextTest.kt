package at.ac.uibk.dps.cirrina.execution.object.context;

public class InMemoryContextTest extends ContextTest {

  @Override
  protected Context createContext() {
    return new InMemoryContext(true);
  }
}
