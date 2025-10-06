package at.ac.uibk.dps.cirrina.execution.`object`.context

import java.io.IOException
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

abstract class ContextTest {
  protected abstract fun createContext(): Context

  @Test
  @Throws(Exception::class)
  fun testOperations() {
    createContext().use { context ->
      assertDoesNotThrow { context.deleteAll() }

      // Create a variable
      assertDoesNotThrow { context.create("testVar", 42) }

      // Assign it a new value
      run {
        val v = assertDoesNotThrow { context.get("testVar") }
        assertEquals(42, v)
      }

      // Try to create it again, which should fail
      assertThrows<IOException> { context.create("testVar", 42) }

      // Non-existent variable should fail
      assertThrows<IOException> { context.get("nonExistentVar") }

      // Deleting a non-existent variable should fail
      assertThrows<IOException> { context.delete("nonExistentVar") }

      // Assigning a value to a non-existent variable should fail
      assertThrows<IOException> { context.assign("nonExistentVar", 1) }

      // Delete the variable
      assertDoesNotThrow { context.delete("testVar") }

      // Deleting it again should fail
      assertThrows<IOException> { context.delete("testVar") }

      // It should not exist anymore
      assertThrows<IOException> { context.get("testVar") }

      // Assigning should fail
      assertThrows<IOException> { context.assign("testVar", 42) }

      // Get all variables
      assertDoesNotThrow {
        context.create("var1", 1)
        context.create("var2", "value2")
      }

      val allVariables = assertDoesNotThrow { context.getAll() }
      assertEquals(2, allVariables.size)
    }
  }

  @Test
  @Throws(Exception::class)
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
      val allVariables = assertDoesNotThrow { context.getAll() }
      assertEquals(
        threadCount * iterationsPerThread,
        allVariables.size,
        "Incorrect number of variables in the context",
      )
    }
  }

  @Test
  @Throws(Exception::class)
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
      val v = assertDoesNotThrow { context.get(variableName) as Int }
      assertEquals(
        iterationsPerThread - 1,
        v,
        "Incorrect final value after multi-threaded setValue",
      )
    }
  }
}
