package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.utils.assertFailure
import at.ac.uibk.dps.cirrina.utils.assertSuccess
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

abstract class ContextTest {
  protected abstract fun createContext(): Context

  @Test
  fun testOperations() {
    createContext().use { context ->
      context.deleteAll().assertSuccess()

      // Create a variable
      context.create("testVar", 42).assertSuccess()

      // Retrieve it should succeed
      context.get("testVar").assertSuccess()

      // Try to create it again, which should fail
      context.create("testVar", 42).assertFailure()

      // Non-existent variable should fail
      context.get("nonExistentVar").assertFailure()

      // Deleting a non-existent variable should fail
      context.delete("nonExistentVar").assertFailure()

      // Assigning a value to a non-existent variable should fail
      context.assign("nonExistentVar", 1).assertFailure()

      // Deleting the variable should succeed
      assertTrue(context.delete("testVar").isSuccess)

      // Deleting it again should fail
      context.delete("testVar").assertFailure()

      // It should not exist anymore
      context.get("testVar").assertFailure()

      // Assigning should fail
      context.assign("testVar", 42).assertFailure()

      // Get all variables
      assertDoesNotThrow {
        context.create("var1", 1)
        context.create("var2", "value2")
      }

      val allVariables = assertDoesNotThrow { context.getAll() }.getOrThrow()
      assertEquals(2, allVariables.size)
    }
  }

  @Test
  fun testMultiThreadedCreateGet() {
    createContext().use { context ->
      assertDoesNotThrow { context.deleteAll() }

      val threadCount = 10
      val iterationsPerThread = 100

      Executors.newFixedThreadPool(threadCount).use { executorService ->
        for (i in 0..<threadCount) {
          executorService.submit {
            assertDoesNotThrow {
              for (j in 0..<iterationsPerThread) {
                val variableName = Thread.currentThread().threadId().toString() + "_" + j

                context.create(variableName, j)
                context.get(variableName)
              }
            }
          }
        }
      }
      val allVariables = assertDoesNotThrow { context.getAll() }.getOrThrow()
      assertEquals(
        threadCount * iterationsPerThread,
        allVariables.size,
        "incorrect number of variables in the context",
      )
    }
  }

  @Test
  fun testMultiThreadedSetValueGetValue() {
    createContext().use { context ->
      assertDoesNotThrow { context.deleteAll() }

      val threadCount = 10
      val iterationsPerThread = 100

      val variableName = "testVar"

      assertDoesNotThrow { context.create(variableName, 0) }

      Executors.newFixedThreadPool(threadCount).use { executorService ->
        for (i in 0..<threadCount) {
          executorService.submit {
            assertDoesNotThrow {
              for (j in 0..<iterationsPerThread) {
                context.assign(variableName, j)
              }
            }
          }
        }
      }
      val v = assertDoesNotThrow { context.get(variableName).getOrThrow() as Int }
      assertEquals(
        iterationsPerThread - 1,
        v,
        "incorrect final value after multi-threaded setValue",
      )
    }
  }
}
