package at.ac.uibk.dps.cirrina.execution.trace.neo4j

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.execution.trace.CausalEvent
import at.ac.uibk.dps.cirrina.execution.trace.CausalTraceRecorder
import at.ac.uibk.dps.cirrina.execution.trace.properties
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import mu.KotlinLogging
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Config
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.TransactionConfig

private val logger = KotlinLogging.logger {}

class Neo4JCausalTraceRecorder(
  private val driver: Driver,
  private val database: String = "neo4j",
  queueCapacity: Int = 10_000,
) : CausalTraceRecorder {
  private val queue = ArrayBlockingQueue<CausalEvent>(queueCapacity)
  private val droppedRecords = AtomicLong()
  private val lifecycleLock = Any()
  private var closed = false

  private var schemaReady = false

  private val executor =
    Executors.newSingleThreadScheduledExecutor { runnable ->
      Thread(runnable, "neo4j-trace-recorder").apply { isDaemon = true }
    }

  init {
    executor.scheduleWithFixedDelay({ flush() }, 100, 100, TimeUnit.MILLISECONDS)
  }

  override fun record(record: CausalEvent) {
    synchronized(lifecycleLock) {
      check(!closed) { "trace recorder is closed" }

      if (!queue.offer(record)) droppedRecords.incrementAndGet()
    }
  }

  private fun reportDroppedRecords() {
    val dropped = droppedRecords.getAndSet(0)

    if (dropped > 0) logger.warn { "trace queue full; dropped $dropped records" }
  }

  private fun flush() {
    reportDroppedRecords()

    val batch = ArrayList<CausalEvent>(500)

    while (!Thread.currentThread().isInterrupted && queue.drainTo(batch, 500) > 0) {
      try {
        writeBatch(batch)
      } catch (exception: Exception) {
        logger.warn {
          "trace delivery failed; discarded ${batch.size} records: ${exception.message}"
        }

        return
      }
      batch.clear()
    }
  }

  private fun writeBatch(batch: List<CausalEvent>) {
    driver.session(SessionConfig.forDatabase(database)).use { session ->
      if (!schemaReady) {
        listOf("TraceRecord", "TraceEvent", "TraceInstance", "TraceRuntime").forEach { label ->
          session
            .run(
              "CREATE CONSTRAINT cirrina_${label}_id IF NOT EXISTS FOR (n:$label) REQUIRE n.id IS UNIQUE"
            )
            .consume()
        }
        schemaReady = true
      }

      val records = batch.map { it.properties() }

      session.executeWrite(
        { tx -> tx.run(WRITE_RECORDS, mapOf("records" to records)).consume() },
        TransactionConfig.builder().withTimeout(Duration.ofSeconds(10)).build(),
      )
    }
  }

  override fun close() {
    synchronized(lifecycleLock) {
      if (closed) return

      closed = true
    }

    executor.execute { flush() }
    executor.shutdown()

    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        logger.warn { "trace shutdown timed out; undelivered records will be discarded" }

        executor.shutdownNow()
      }
    } catch (_: InterruptedException) {
      executor.shutdownNow()

      Thread.currentThread().interrupt()
    } finally {
      val pending = queue.size
      queue.clear()

      if (pending > 0) logger.warn { "trace shutdown discarded $pending queued records" }
      reportDroppedRecords()

      driver.close()
    }
  }

  companion object {
    fun create(): Neo4JCausalTraceRecorder {
      val driver =
        GraphDatabase.driver(
          EnvironmentVariables.neo4jUri.get(),
          AuthTokens.basic(
            EnvironmentVariables.neo4jUser.get(),
            EnvironmentVariables.neo4jPassword.get(),
          ),
          Config.builder()
            .withConnectionTimeout(2, TimeUnit.SECONDS)
            .withConnectionAcquisitionTimeout(2, TimeUnit.SECONDS)
            .withMaxTransactionRetryTime(2, TimeUnit.SECONDS)
            .build(),
        )

      return Neo4JCausalTraceRecorder(driver, EnvironmentVariables.neo4jDatabase.get())
    }

    private val WRITE_RECORDS =
      """
      UNWIND ${'$'}records AS record

      MERGE (runtime:TraceRuntime {id: record.runtimeId})
      MERGE (instance:TraceInstance {id: record.instanceId})
      SET instance.name = record.instance, instance.stateMachine = record.stateMachine
      MERGE (instance)-[:IN_RUNTIME]->(runtime)

      MERGE (n:TraceRecord {id: record.id})
      SET n += record
      MERGE (n)-[:IN_INSTANCE]->(instance)

      FOREACH (_ IN CASE WHEN record.type = 'INSTANCE_CREATED' THEN [1] ELSE [] END | SET n:InstanceCreated)
      FOREACH (_ IN CASE WHEN record.type = 'STATE_ENTERED' THEN [1] ELSE [] END | SET n:StateEntered)

      FOREACH (_ IN CASE WHEN record.causalParent IS NOT NULL THEN [1] ELSE [] END |
        MERGE (parent:TraceRecord {id: record.causalParent})
        MERGE (n)-[:CAUSED_BY]->(parent)
      )

      FOREACH (_ IN CASE WHEN record.previousRecord IS NOT NULL THEN [1] ELSE [] END |
        MERGE (previous:TraceRecord {id: record.previousRecord})
        MERGE (previous)-[:NEXT]->(n)
      )

      FOREACH (_ IN CASE WHEN record.type = 'EVENT_EMITTED' THEN [1] ELSE [] END |
        SET n:EventEmitted
        MERGE (event:TraceEvent {id: record.eventId})
        SET event.topic = record.event, event.channel = record.channel,
            event.source = record.instance, event.target = record.target
        MERGE (n)-[:EMITS]->(event)
      )

      FOREACH (_ IN CASE WHEN record.type = 'EVENT_HANDLED' THEN [1] ELSE [] END |
        SET n:EventHandled
        MERGE (event:TraceEvent {id: record.eventId})
        SET event.topic = record.event, event.channel = record.channel,
            event.source = record.source, event.target = record.target
        MERGE (n)-[:HANDLES]->(event)
      )
      """
        .trimIndent()
  }
}
