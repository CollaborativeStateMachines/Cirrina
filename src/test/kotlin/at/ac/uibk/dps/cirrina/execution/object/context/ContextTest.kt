package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

abstract class ContextTest {

  protected abstract fun createContext(): Context

  @Test
  fun testOperations() =
    createContext().use { context ->
      context.deleteAll()

      // Create and retrieve
      context.create("testVar", 42)
      assertEquals(42, context.get("testVar"))

      // Duplicate creation should fail
      assertThrows<IllegalStateException> { context.create("testVar", 42) }

      // Accessing/Modifying non-existent variables should fail
      assertThrows<IllegalStateException> { context.get("missing") }
      assertThrows<IllegalStateException> { context.delete("missing") }
      assertThrows<IllegalStateException> { context.assign("missing", 1) }

      // Deletion lifecycle
      context.delete("testVar")
      assertThrows<IllegalStateException> { context.delete("testVar") }
      assertThrows<IllegalStateException> { context.get("testVar") }
      assertThrows<IllegalStateException> { context.assign("testVar", 42) }

      // Bulk operations
      context.create("var1", 1)
      context.create("var2", "value2")

      assertEquals(2, context.getAll().size)
    }

  @Test
  fun testMultiThreadedCreateGet() =
    createContext().use { context ->
      context.deleteAll()

      val threadCount = 10
      val iterationsPerThread = 100

      Executors.newFixedThreadPool(threadCount).use { executor ->
        repeat(threadCount) {
          executor.submit {
            repeat(iterationsPerThread) { j ->
              val varName = "thread_${Thread.currentThread().threadId()}_$j"
              context.create(varName, j)
              assertEquals(j, context.get(varName))
            }
          }
        }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
      }

      assertEquals(
        threadCount * iterationsPerThread,
        context.getAll().size,
        "incorrect number of variables in the context",
      )
    }

  @Test
  fun testMultiThreadedSetValueGetValue() =
    createContext().use { context ->
      context.deleteAll()

      val threadCount = 10
      val iterationsPerThread = 100
      val varName = "testVar"

      context.create(varName, 0)

      Executors.newFixedThreadPool(threadCount).use { executor ->
        repeat(threadCount) {
          executor.submit { repeat(iterationsPerThread) { j -> context.assign(varName, j) } }
        }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
      }

      assertEquals(
        iterationsPerThread - 1,
        context.get(varName) as Int,
        "incorrect final value after multi-threaded assign",
      )
    }
}
