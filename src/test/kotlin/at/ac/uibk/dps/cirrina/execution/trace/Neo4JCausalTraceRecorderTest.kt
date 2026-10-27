package at.ac.uibk.dps.cirrina.execution.trace

import at.ac.uibk.dps.cirrina.execution.trace.neo4j.Neo4JCausalTraceRecorder
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.MockMakers
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.neo4j.driver.Driver
import org.neo4j.driver.Result
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.TransactionCallback
import org.neo4j.driver.TransactionConfig
import org.neo4j.driver.TransactionContext
import org.neo4j.driver.exceptions.ServiceUnavailableException
import org.neo4j.driver.summary.ResultSummary

@Timeout(15)
class Neo4JCausalTraceRecorderTest {
  private inline fun <reified T : Any> mockInterface(): T =
    mock(T::class.java, withSettings().mockMaker(MockMakers.PROXY))

  private val driver = mockInterface<Driver>()
  private val session = mockInterface<Session>()
  private val transaction = mockInterface<TransactionContext>()
  private val result = mockInterface<Result>()
  private val batches = CopyOnWriteArrayList<List<Map<String, Any?>>>()
  private var onWrite: () -> Unit = {}

  init {
    whenever(driver.session(any<SessionConfig>())).thenReturn(session)
    whenever(session.run(any<String>())).thenReturn(result)
    whenever(transaction.run(any<String>(), any<Map<String, Any?>>())).thenAnswer { invocation ->
      val parameters = invocation.getArgument<Map<String, List<Map<String, Any?>>>>(1)
      batches.add(parameters.getValue("records"))
      onWrite()
      result
    }
    whenever(
        session.executeWrite(any<TransactionCallback<ResultSummary>>(), any<TransactionConfig>())
      )
      .thenAnswer { invocation ->
        invocation.getArgument<TransactionCallback<ResultSummary>>(0).execute(transaction)
      }
  }

  @Test
  fun `a blocked database and full queue do not block event handlers`() {
    val writing = CountDownLatch(1)
    val release = CountDownLatch(1)

    onWrite = {
      writing.countDown()
      check(release.await(5, TimeUnit.SECONDS))
    }

    val records = List(4) { CausalEvent.InstanceCreated("instance", "machine") }
    val recorder = Neo4JCausalTraceRecorder(driver, queueCapacity = 2)

    try {
      recorder.record(records[0])

      assertTrue(writing.await(5, TimeUnit.SECONDS))
      assertTimeoutPreemptively(Duration.ofSeconds(1)) { records.drop(1).forEach(recorder::record) }
    } finally {
      release.countDown()
      recorder.close()
    }

    assertEquals(records.take(3).map { it.id }, batches.flatten().map { it["id"] })
    assertThrows(IllegalStateException::class.java) { recorder.record(records[3]) }

    recorder.close()

    verify(driver, times(1)).close()
  }

  @Test
  fun `shutdown drains all accepted records in bounded batches`() {
    val records = List(1200) { CausalEvent.InstanceCreated("instance", "machine") }
    Neo4JCausalTraceRecorder(driver).use { recorder -> records.forEach(recorder::record) }

    assertEquals(records.map { it.id }, batches.flatten().map { it["id"] })
    assertTrue(batches.size >= 3)
    assertTrue(batches.all { it.size in 1..500 })

    verify(driver).close()
  }

  @Test
  fun `a failed batch is discarded and the worker continues with later records`() {
    val firstWrite = AtomicBoolean(true)
    val failed = CountDownLatch(1)
    val delivered = CountDownLatch(1)

    onWrite = {
      if (firstWrite.getAndSet(false)) {
        failed.countDown()
        throw ServiceUnavailableException("test database unavailable")
      }
      delivered.countDown()
    }

    val records = List(2) { CausalEvent.InstanceCreated("instance", "machine") }
    Neo4JCausalTraceRecorder(driver).use { recorder ->
      recorder.record(records[0])
      assertTrue(failed.await(5, TimeUnit.SECONDS))
      recorder.record(records[1])
      assertTrue(delivered.await(5, TimeUnit.SECONDS))
    }

    assertEquals(records.map { it.id }, batches.flatten().map { it["id"] })
    assertEquals(2, batches.size)
  }
}
