package at.ac.uibk.dps.cirrina.execution.`object`

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions
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
      Assertions.assertEquals(42, context.get("testVar"))

      // Duplicate creation should fail
      assertThrows<Exception> { context.create("testVar", 42) }

      // Accessing/Modifying non-existent variables should fail
      assertThrows<Exception> { context.get("missing") }
      assertThrows<Exception> { context.delete("missing") }
      assertThrows<Exception> { context.assign("missing", 1) }

      // Deletion lifecycle
      context.delete("testVar")
      assertThrows<Exception> { context.delete("testVar") }
      assertThrows<Exception> { context.get("testVar") }
      assertThrows<Exception> { context.assign("testVar", 42) }

      // Bulk operations
      context.create("var1", 1)
      context.create("var2", "value2")

      Assertions.assertEquals(2, context.getAll().size)
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
              Assertions.assertEquals(j, context.get(varName))
            }
          }
        }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
      }

      Assertions.assertEquals(
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

      Assertions.assertEquals(
        iterationsPerThread - 1,
        context.get(varName) as Int,
        "incorrect final value after multi-threaded assign",
      )
    }
}
