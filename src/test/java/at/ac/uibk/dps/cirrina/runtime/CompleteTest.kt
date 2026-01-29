package at.ac.uibk.dps.cirrina.runtime

import InMemoryEventHandler
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.GLOBAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler.Companion.PERIPHERAL_SOURCE
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventProtos
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Stdlib
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockHttpServer
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CompleteTest {

  @Test
  fun testCompleteExecute() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val eventHandler =
          InMemoryEventHandler().apply {
            subscribe(GLOBAL_SOURCE)
            subscribe(PERIPHERAL_SOURCE)
          }
        val context = InMemoryContext()
        val server = mockHttpServer { input ->
          val v = input.firstOrNull { it.name == "v" } ?: error("variable 'v' not found")
          listOf(ContextVariable("v", (v.value as Int) + 1))
        }

        try {
          val service =
            ServiceImplementationBindings.HttpServiceImplementationBinding(
              "increment",
              true,
              ServiceImplementationBindings.Type.HTTP,
              "http",
              "localhost",
              8000,
              "/increment",
              ServiceImplementationBindings.HttpMethod.GET,
            )

          val selector =
            RandomServiceImplementationSelector(
              ServiceImplementationBuilder.from(listOf(service)).build().getOrThrow()
            )

          val runtime =
            DaggerTestComponent.builder()
              .testModule(TestModule(eventHandler, context, selector, DefaultDescriptions.complete))
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
    assertTrue(sizeSet.toIntArray()[0] in sizes)

    assertTrue(Stdlib.takeRandom(sizeSet) in sizeSet)
  }

  @Test
  fun testProtos() {
    val integerValue = ContextVariableProtos.Value.newBuilder().setInteger(123).build()
    assertEquals(123, integerValue.integer)
    assertEquals(ContextVariableProtos.Value.ValueCase.INTEGER, integerValue.valueCase)

    val stringValue = ContextVariableProtos.Value.newBuilder().setString("test").build()
    assertEquals("test", stringValue.string)
    assertEquals(ContextVariableProtos.Value.ValueCase.STRING, stringValue.valueCase)

    val boolValue = ContextVariableProtos.Value.newBuilder().setBool(true).build()
    assertTrue(boolValue.bool)
    assertEquals(ContextVariableProtos.Value.ValueCase.BOOL, boolValue.valueCase)

    val contextVariable =
      ContextVariableProtos.ContextVariable.newBuilder()
        .setName("test")
        .setValue(integerValue)
        .build()

    assertEquals("test", contextVariable.name)
    assertEquals(123, contextVariable.value.integer)

    val event =
      EventProtos.Event.newBuilder()
        .setTopic("testEvent")
        .setChannel(EventProtos.Event.Channel.PERIPHERAL)
        .addData(contextVariable)
        .setSource("source")
        .setId("someId")
        .setCreatedTime(1)
        .build()

    assertThrows<NullPointerException> {
      EventProtos.Event.newBuilder().setChannel(EventProtos.Event.Channel.forNumber(15))
    }

    assertNotNull(event)
    assertEquals("testEvent", event.topic)
    assertEquals(EventProtos.Event.Channel.PERIPHERAL, event.channel)
    assertEquals(123, event.getData(0).value.integer)
    assertEquals("source", event.source)
    assertEquals("someId", event.id)
    assertEquals(event, EventProtos.Event.parseFrom(event.toByteArray()))

    val valueCollection =
      ContextVariableProtos.ValueCollection.newBuilder()
        .addEntry(integerValue)
        .addEntry(stringValue)
        .build()

    val collectionValue = ContextVariableProtos.Value.newBuilder().setList(valueCollection).build()
    assertEquals(2, collectionValue.list.entryCount)

    val mapEntry =
      ContextVariableProtos.ValueMapEntry.newBuilder()
        .setKey(stringValue)
        .setValue(boolValue)
        .build()

    val valueMap = ContextVariableProtos.ValueMap.newBuilder().addEntry(mapEntry).build()
    val mapValue = ContextVariableProtos.Value.newBuilder().setMap(valueMap).build()

    assertTrue(mapValue.hasMap())
    assertEquals("test", mapValue.map.getEntry(0).key.string)

    val parsed = ContextVariableProtos.ContextVariable.parseFrom(contextVariable.toByteArray())
    assertEquals(contextVariable.name, parsed.name)
    assertEquals(contextVariable.value.integer, parsed.value.integer)
  }

  @Test
  fun testServiceImplementationBindings() {
    val service =
      ServiceImplementationBindings.HttpServiceImplementationBinding(
        "increment",
        true,
        ServiceImplementationBindings.Type.HTTP,
        "http",
        "localhost",
        8000,
        "/increment",
        ServiceImplementationBindings.HttpMethod.GET,
      )

    assertEquals("copy", service.withName("copy").name)
    assertEquals(
      ServiceImplementationBindings.Type.HTTP,
      service.withType(ServiceImplementationBindings.Type.HTTP).type,
    )
    assertFalse(service.withLocal(false).isLocal)
    assertEquals("otherScheme", service.withScheme("otherScheme").scheme)
    assertEquals("otherHost", service.withHost("otherHost").host)
    assertEquals(8080, service.withPort(8080).port)
    assertEquals("/somethingElse", service.withEndPoint("/somethingElse").endPoint)
    assertEquals(
      ServiceImplementationBindings.HttpMethod.POST,
      service.withMethod(ServiceImplementationBindings.HttpMethod.POST).method,
    )

    val otherService = service.withName("otherService")
    assertEquals(service, service)
    assertEquals(service.hashCode(), service.hashCode())
    assertNotEquals(service, otherService)
  }
}
