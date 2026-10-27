package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.execution.trace.CausalEvent
import at.ac.uibk.dps.cirrina.execution.trace.CausalTraceRecorder
import at.ac.uibk.dps.cirrina.spec.Event
import at.ac.uibk.dps.cirrina.util.TestUtils
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class CollectingCausalTraceRecorder : CausalTraceRecorder {
  val records = ConcurrentLinkedQueue<CausalEvent>()
  var closed = false

  override fun record(record: CausalEvent) {
    records.add(record)
  }

  override fun close() {
    closed = true
  }
}

internal fun tracedRuntime(
  uri: URI,
  instances: List<String>,
  recorder: CausalTraceRecorder,
): Runtime =
  DaggerTestComponent.builder()
    .testModule(TestModule(ContextInMemory(), uri, instances, recorder))
    .build()
    .runtime()

internal fun awaitTrace(predicate: () -> Boolean) {
  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)

  while (!predicate() && System.nanoTime() < deadline) Thread.sleep(5)

  assertTrue(predicate(), "timed out waiting for trace")
}

@Timeout(20)
class TraceTest {
  @Test
  fun `reusing an instance name keeps separate trace lifetimes`() {
    val recordings =
      List(2) {
        val recorder = CollectingCausalTraceRecorder()
        val runtime =
          tracedRuntime(TestUtils.resourceUri("pkl/trace/loop.pkl"), listOf("loop"), recorder)

        awaitTrace { recorder.records.filterIsInstance<CausalEvent.EventHandled>().count() == 4 }

        runtime(Event("resume", EventChannel.PERIPHERAL))
        runtime.run()

        recorder.records.toList()
      }

    val creations = recordings.map { it.filterIsInstance<CausalEvent.InstanceCreated>().single() }

    assertEquals(listOf("loop", "loop"), creations.map { it.instance })
    assertEquals(2, creations.map { it.instanceId }.toSet().size)
    assertEquals(2, creations.map { it.runtimeId }.toSet().size)

    recordings.zip(creations).forEach { (records, created) ->
      assertNotEquals(created.instance, created.instanceId)
      assertEquals(setOf(created.instanceId), records.map { it.instanceId }.toSet())
    }
  }

  @Test
  fun `loops keep unique emissions and continue after an external event`() {
    val recorder = CollectingCausalTraceRecorder()
    val runtime =
      tracedRuntime(TestUtils.resourceUri("pkl/trace/loop.pkl"), listOf("loop"), recorder)

    awaitTrace { recorder.records.filterIsInstance<CausalEvent.EventHandled>().count() == 4 }

    val resume = Event("resume", EventChannel.PERIPHERAL, source = "sensor")

    runtime(resume)
    runtime.run()

    val records = recorder.records.toList()
    val emitted = records.filterIsInstance<CausalEvent.EventEmitted>()

    assertEquals(4, emitted.size)
    assertEquals(4, emitted.map { it.eventId }.toSet().size)

    val handled = records.filterIsInstance<CausalEvent.EventHandled>()

    assertEquals(5, handled.size)
    assertEquals(
      emitted.map { it.eventId }.toSet(),
      handled.filter { it.event == "tick" }.map { it.eventId }.toSet(),
    )

    val external = handled.single { it.event == "resume" }

    assertEquals("waiting", external.stateBefore)
    assertEquals("done", external.stateAfter)
    assertEquals(resume.id, external.eventId)
    assertNotNull(external.previousRecord)
    assertNull(external.causalParent)

    val byId = records.associateBy { it.id }

    assertEquals(records.size, byId.size)
    records.forEach { record ->
      record.causalParent?.let { assertTrue(it in byId) }
      record.previousRecord?.let { assertTrue(it in byId) }
      assertTrue(record.runtimeId.isNotBlank())
      assertTrue(record.instanceId.isNotBlank())
    }
    handled.forEach { handling ->
      assertEquals(
        handling.emittedEventIds.toSet(),
        emitted.filter { it.causalParent == handling.id }.map { it.eventId }.toSet(),
      )
    }
    assertTrue(recorder.closed)
  }

  @Test
  fun `asynchronous completion retains the event that launched it`() {
    val recorder = CollectingCausalTraceRecorder()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    val server =
      TestUtils.mockHttpServer { input ->
        entered.countDown()
        check(release.await(5, TimeUnit.SECONDS))
        input
      }

    try {
      val runtime =
        tracedRuntime(TestUtils.resourceUri("pkl/trace/async.pkl"), listOf("async"), recorder)

      runtime(Event("begin", EventChannel.PERIPHERAL))

      assertTrue(entered.await(5, TimeUnit.SECONDS))

      runtime(Event("unrelated", EventChannel.PERIPHERAL))

      awaitTrace {
        recorder.records.filterIsInstance<CausalEvent.EventHandled>().any {
          it.event == "unrelated"
        }
      }

      release.countDown()
      runtime.run()

      val begin =
        recorder.records.filterIsInstance<CausalEvent.EventHandled>().single { it.event == "begin" }
      val completion = recorder.records.filterIsInstance<CausalEvent.EventEmitted>().single()

      assertEquals(begin.id, completion.causalParent)
      assertEquals("working", completion.state)
      assertTrue(begin.emittedEventIds.isEmpty())
    } finally {
      release.countDown()
      server.stop(0)
    }
  }

  @Test
  fun `dynamic instances and timers retain their causes`() {
    val recorder = CollectingCausalTraceRecorder()
    tracedRuntime(DefaultDescriptions.instantiate, listOf("instantiator", "consumer"), recorder)
      .run()
    val records = recorder.records.associateBy { it.id }
    val created = recorder.records.filterIsInstance<CausalEvent.InstanceCreated>()
    val dynamic = created.filter { it.stateMachine == "instantiated" }

    assertEquals(10, dynamic.size)
    assertEquals(created.size, created.map { it.instanceId }.toSet().size)
    dynamic.forEach {
      val parent = records[it.causalParent] as CausalEvent.EventHandled
      assertEquals("instantiate", parent.event)
    }
    recorder.records
      .filterIsInstance<CausalEvent.EventEmitted>()
      .filter { it.event == "instantiate" }
      .forEach { assertTrue(records[it.causalParent] is CausalEvent.StateEntered) }
  }

  @Test
  fun `nested forwarding records a single emission from the actual source`() {
    val recorder = CollectingCausalTraceRecorder()
    val server =
      TestUtils.mockHttpServer { input ->
        input.map { at.ac.uibk.dps.cirrina.spec.ContextVariable(it.name, (it.value as Int) + 1) }
      }

    try {
      tracedRuntime(DefaultDescriptions.complete, listOf("complete"), recorder).run()

      val nested =
        recorder.records.filterIsInstance<CausalEvent.EventEmitted>().filter { it.event == "ne1" }

      assertEquals(1, nested.size)
      assertEquals("complete@nestedStateMachine", nested.single().instance)

      val created = recorder.records.filterIsInstance<CausalEvent.InstanceCreated>()

      assertEquals(
        created.single { it.instance == "complete" }.id,
        created.single { it.instance.contains('@') }.causalParent,
      )
    } finally {
      server.stop(0)
    }
  }
}
