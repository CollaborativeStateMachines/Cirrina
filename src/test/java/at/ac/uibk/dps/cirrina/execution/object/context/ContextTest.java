package at.ac.uibk.dps.cirrina.execution.object.context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

public abstract class ContextTest {

  protected abstract Context createContext();

  @Test
  void testCreateAndGetContextVariable() throws Exception {
    try (var context = createContext()) {
      assertDoesNotThrow(() -> context.create("testVar", 42));

      var v = assertDoesNotThrow(() -> context.get("testVar"));
      assertEquals(42, v);
    }
  }

  @Test
  void testCreateDuplicateVariable() throws Exception {
    try (var context = createContext()) {
      assertDoesNotThrow(() -> context.create("testVar", 42));
      assertThrows(IOException.class, () -> context.create("testVar", 42));
    }
  }

  @Test
  void testGetNonExistentVariable() throws Exception {
    try (var context = createContext()) {
      assertThrows(IOException.class, () -> context.get("nonExistentVar"));
    }
  }

  @Test
  void testAssign() throws Exception {
    try (var context = createContext()) {
      assertDoesNotThrow(() -> {
        context.create("testVar", 42);
        context.assign("testVar", 100);
      });

      var v = assertDoesNotThrow(() -> context.get("testVar"));
      assertEquals(100, v);
    }
  }

  @Test
  void testAssignNonExistentVariable() throws Exception {
    try (var context = createContext()) {
      assertThrows(IOException.class, () -> context.assign("nonExistentVar", 1));
    }
  }

  @Test
  void testDelete() throws Exception {
    try (var context = createContext()) {
      assertDoesNotThrow(() -> {
        context.create("testVar", 42);
        context.delete("testVar");
      });

      assertThrows(IOException.class, () -> context.get("testVar"));
    }
  }

  @Test
  void testDeleteNonExistentVariable() throws Exception {
    try (var context = createContext()) {
      assertThrows(IOException.class, () -> context.delete("nonExistentVar"));
    }
  }

  @Test
  void testGetAll() throws Exception {
    try (var context = createContext()) {
      assertDoesNotThrow(() -> {
        context.create("var1", 1);
        context.create("var2", "value2");
      });

      var allVariables = assertDoesNotThrow(context::getAll);
      assertEquals(2, allVariables.size());
    }
  }

  @Test
  void testMultiThreadedCreateGet() throws Exception {
    try (var context = createContext()) {
      final int threadCount = 10;
      final int iterationsPerThread = 100;

      try (var executorService = Executors.newFixedThreadPool(threadCount)) {
        for (int i = 0; i < threadCount; ++i) {
          executorService.submit(() ->
            assertDoesNotThrow(() -> {
              for (int j = 0; j < iterationsPerThread; ++j) {
                var variableName = Thread.currentThread().threadId() + "_" + j;

                context.create(variableName, j);
                context.get(variableName);
              }
            })
          );
        }
      }

      var allVariables = assertDoesNotThrow(context::getAll);
      assertEquals(
        threadCount * iterationsPerThread,
        allVariables.size(),
        "Incorrect number of variables in the context"
      );
    }
  }

  @Test
  void testMultiThreadedSetValueGetValue() throws Exception {
    try (var context = createContext()) {
      final int threadCount = 10;
      final int iterationsPerThread = 100;

      var variableName = "testVar";

      assertDoesNotThrow(() -> context.create(variableName, 0));

      try (var executorService = Executors.newFixedThreadPool(threadCount)) {
        for (int i = 0; i < threadCount; ++i) {
          executorService.submit(() ->
            assertDoesNotThrow(() -> {
              for (int j = 0; j < iterationsPerThread; ++j) {
                context.assign(variableName, j);
              }
            })
          );
        }
      }

      var v = assertDoesNotThrow(() -> (int) context.get(variableName));
      assertEquals(
        iterationsPerThread - 1,
        v,
        "Incorrect final value after multi-threaded setValue"
      );
    }
  }
}
