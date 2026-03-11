package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.Csml.HttpMethod
import at.ac.uibk.dps.cirrina.csm.Csml.HttpServiceImplementationBinding
import at.ac.uibk.dps.cirrina.csm.Csml.Type
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.Stdlib
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import at.ac.uibk.dps.cirrina.util.TestUtils.mockHttpServer
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompleteTest {
  @Test
  fun testCompleteExecute() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val context = ContextInMemory()
        val server = mockHttpServer { input ->
          val v = input.firstOrNull { it.name == "v" } ?: error("variable 'v' not found")
          listOf(ContextVariable("v", (v.value as Int) + 1))
        }

        try {
          val runtime =
            DaggerTestComponent.builder()
              .testModule(TestModule(context, DefaultDescriptions.complete, listOf("complete")))
              .build()
              .runtime()

          val duration = measureTime { runtime.run() }
          println("complete execution: $duration")

          assertEquals(100, context.get("v"))
          assertEquals(true, context.get("b"))
          assertEquals(true, context.get("e"))
        } finally {
          server.stop(1)
        }
      }
    }
  }

  @Test
  fun testStdlib() {
    val sizes = intArrayOf(10, 50, 100, 200, 500)
    val sizeSet = HashSet<Int>()

    repeat(100) {
      val payload = Stdlib.randomPayload(sizes)
      assertNotNull(payload)
      assertTrue(sizes.any { it == payload.size })
      sizeSet.add(payload.size)
    }

    assertTrue(sizeSet.size > 1)
    assertTrue(sizeSet.first() in sizes)

    assertTrue(Stdlib.takeRandom(sizeSet) in sizeSet)
  }

  @Test
  fun testSerialization() {
    val intVal = 123
    assertEquals(intVal, Serializer.deserialize(Serializer.serialize(intVal)))

    val stringVal = "test"
    assertEquals(stringVal, Serializer.deserialize<String>(Serializer.serialize(stringVal)))

    val cv = ContextVariable("testVar", 456)
    val cvParsed = Serializer.deserialize<ContextVariable>(Serializer.serialize(cv))
    assertEquals(cv.name, cvParsed.name)
    assertEquals(cv.value, cvParsed.value)

    val event =
      Event(
        topic = "testEvent",
        channel = Csml.EventChannel.PERIPHERAL,
        data = listOf(cv),
        target = "target",
        source = "source",
        id = "someId",
        createdTime = 1L,
      )

    val eventParsed = Serializer.deserialize<Event>(Serializer.serialize(event))
    assertEquals(event.topic, eventParsed.topic)
    assertEquals(event.channel, eventParsed.channel)
    assertEquals(1, eventParsed.data.size)
    assertEquals(456, eventParsed.data[0].value)
    assertEquals("source", eventParsed.source)

    val complexMap = mapOf("key" to listOf(1, 2, 3), "active" to true)
    val mapParsed = Serializer.deserialize<Map<String, Any>>(Serializer.serialize(complexMap))
    assertEquals(complexMap, mapParsed)
    assertEquals(3, (mapParsed["key"] as List<*>).size)
  }

  @Test
  fun testServiceImplementationBindings() {
    val service =
      HttpServiceImplementationBinding(
        "increment",
        true,
        Type.HTTP,
        "http",
        "localhost",
        8000,
        "/increment",
        HttpMethod.GET,
      )

    assertEquals("copy", service.withName("copy").name)
    assertEquals(Type.HTTP, service.withType(Type.HTTP).type)
    assertFalse(service.withLocal(false).isLocal)
    assertEquals("otherScheme", service.withScheme("otherScheme").scheme)
    assertEquals("otherHost", service.withHost("otherHost").host)
    assertEquals(8080, service.withPort(8080).port)
    assertEquals("/somethingElse", service.withEndPoint("/somethingElse").endPoint)
    assertEquals(HttpMethod.POST, service.withMethod(HttpMethod.POST).method)

    val otherService = service.withName("otherService")
    assertEquals(service, service)
    assertEquals(service.hashCode(), service.hashCode())
    assertNotEquals(service, otherService)
  }
}
