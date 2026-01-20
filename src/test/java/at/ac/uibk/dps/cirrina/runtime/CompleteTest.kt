package at.ac.uibk.dps.cirrina.runtime

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.InMemoryContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventProtos
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Stdlib
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.utils.BuildVersion
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockHttpServer
import java.time.Duration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CompleteTest {

  private inner class MockEventHandler : EventHandler() {
    var lastPropagatedEvent: Event? = null
      private set

    override fun close() {}

    override fun sendEvent(event: Event, source: String?) = propagateEvent(event)

    override fun subscribe(topic: String) {}

    override fun unsubscribe(topic: String) {}

    override fun subscribe(source: String, subject: String) {}

    override fun unsubscribe(source: String, subject: String) {}

    override fun propagateEvent(event: Event) {
      this.lastPropagatedEvent = event
      super.propagateEvent(event)
    }
  }

  @Test
  fun testCompleteExecute() {
    val buildVersion = BuildVersion.getBuildVersion()
    assertNull(buildVersion)

    // Must finish within ten seconds
    assertTimeout(Duration.ofSeconds(10)) {
      // Should not throw any exception
      assertDoesNotThrow {
        MockEventHandler().use { eventHandler ->
          InMemoryContext(false).use { context ->
            val server = mockHttpServer { input ->
              val v = input.firstOrNull { it.name == "v" } ?: error("Variable 'v' not found")
              listOf(ContextVariable("v", (v.value as Int) + 1))
            }
            try {
              // Create a map from service types to service implementations
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

              val services = ServiceImplementationBuilder.from(listOf(service)).build().getOrThrow()
              val serviceImplementationSelector = RandomServiceImplementationSelector(services)

              // Create and run the runtime using one state machine (completeStateMachine)
              val runtime =
                Runtime(
                    DefaultDescriptions.complete,
                    listOf("completeStateMachine"),
                    serviceImplementationSelector,
                    eventHandler,
                    context,
                  )
                  .apply { run() }

              // Retrieve all state machine instances registered with the runtime
              val allStateMachines = runtime.stateMachines.toMutableList()

              // Should be "completeStateMachine"
              assertEquals(
                allStateMachines.first().stateMachineClass.toString(),
                "completeStateMachine",
              )

              allStateMachines.removeAt(0)

              // Should contain nestedStateMachine after removing statemachine1
              assertEquals(allStateMachines.size, 1)

              // Should have state a
              assertEquals(
                allStateMachines.first().stateMachineClass.findStateClassByName("a")!!.toString(),
                "a",
              )

              // Should not have state b
              assertNull(allStateMachines.first().stateMachineClass.findStateClassByName("b"))

              assertEquals(context.get("v"), 100)
              assertEquals(context.get("b"), true)
              assertEquals(context.get("e"), true)
            } finally {
              server.stop(1)
            }
          }
        }
      }
    }
  }

  @Test
  fun testStdlib() {
    val sizes = intArrayOf(10, 50, 100, 200, 500)
    val sizeSet = HashSet<Int>()

    repeat(100) {
      val payload = Stdlib.genRandPayload(sizes)
      assertNotNull(payload)
      assertTrue(sizes.any { it == payload.size })
      sizeSet.add(payload.size)
    }

    // Should contain at least two different payloads
    assertTrue(sizeSet.size > 1)

    // Should be a payload from sizes
    assertTrue(sizeSet.toIntArray()[0] in sizes)
  }

  @Test
  fun testProtos() {
    // Build integer ContextVariableProtos.Value
    val integerValue = ContextVariableProtos.Value.newBuilder().setInteger(123).build()
    assertEquals(123, integerValue.integer)
    assertTrue(integerValue.hasInteger())
    assertEquals(ContextVariableProtos.Value.ValueCase.INTEGER, integerValue.valueCase)

    // Build string ContextVariableProtos value
    val stringValue = ContextVariableProtos.Value.newBuilder().setString("test").build()
    assertEquals("test", stringValue.string)
    assertTrue(stringValue.hasString())
    assertEquals(ContextVariableProtos.Value.ValueCase.STRING, stringValue.valueCase)

    // Build boolean ContextVariableProtos value
    val boolValue = ContextVariableProtos.Value.newBuilder().setBool(true).build()
    assertTrue(boolValue.bool)
    assertTrue(boolValue.hasBool())
    assertEquals(ContextVariableProtos.Value.ValueCase.BOOL, boolValue.valueCase)

    // Test ContextVariable message
    val contextVariable =
      ContextVariableProtos.ContextVariable.newBuilder()
        .setName("test")
        .setValue(integerValue)
        .build()

    assertNotNull(contextVariable.nameBytes)

    // Should have name and value as defined
    assertEquals("test", contextVariable.name)
    assertTrue(contextVariable.hasValue())
    assertEquals(123, contextVariable.value.integer)

    // Test EventProtos.Event
    val event =
      EventProtos.Event.newBuilder()
        .setCreatedTime(1.0)
        .setName("testEvent")
        .setId("someId")
        // Set channel three times to go through each case
        .setChannel(EventProtos.Event.Channel.INTERNAL)
        .setChannel(EventProtos.Event.Channel.EXTERNAL)
        .setChannel(EventProtos.Event.Channel.GLOBAL)
        .setChannel(EventProtos.Event.Channel.PERIPHERAL)
        .addData(contextVariable)
        .build()

    // Should return null if channel number is outside of 0-3
    assertThrows<NullPointerException> {
      EventProtos.Event.newBuilder().setChannel(EventProtos.Event.Channel.forNumber(15))
    }

    // Should have details as defined
    assertNotNull(event)
    assertTrue(event.createdTime > 0.0)
    assertEquals(event.name, "testEvent")
    assertEquals(event.id, "someId")
    assertEquals(event.channel, EventProtos.Event.Channel.PERIPHERAL)

    // Should be equal after serializing and deserializing
    assertEquals(event, EventProtos.Event.parseFrom(event.toByteArray()))

    // Should have data equal to the added ContextVariable
    assertEquals(event.getData(0).name, "test")
    assertEquals(event.getData(0).value.integer, 123)

    // Test ContextVariableProtos.ValueCollection
    val valueCollection =
      ContextVariableProtos.ValueCollection.newBuilder()
        .addEntry(integerValue)
        .addEntry(stringValue)
        .build()

    val collectionValue = ContextVariableProtos.Value.newBuilder().setList(valueCollection).build()

    // Should be the same list added to collectionValue
    assertTrue(collectionValue.hasList())
    assertEquals(2, collectionValue.list.entryCount)
    assertEquals(123, collectionValue.list.getEntry(0).integer)

    // Test ContextVariableProtos.ValueMapEntry
    val mapEntry =
      ContextVariableProtos.ValueMapEntry.newBuilder()
        .setKey(stringValue)
        .setValue(boolValue)
        .build()

    val valueMap = ContextVariableProtos.ValueMap.newBuilder().addEntry(mapEntry).build()

    val mapValue = ContextVariableProtos.Value.newBuilder().setMap(valueMap).build()

    // Should be the correct map in mapValue
    assertTrue(mapValue.hasMap())
    assertEquals(1, mapValue.map.entryCount)
    assertEquals("test", mapValue.map.getEntry(0).key.string)
    assertTrue(mapValue.map.getEntry(0).value.bool)

    // Should be equal after serializing and deserializing
    assertEquals(
      contextVariable.name,
      ContextVariableProtos.ContextVariable.parseFrom(contextVariable.toByteArray()).name,
    )
    assertEquals(
      contextVariable.value.integer,
      ContextVariableProtos.ContextVariable.parseFrom(contextVariable.toByteArray()).value.integer,
    )
  }

  @Test
  fun testServiceImplementationBindings() {
    // Define test service
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

    // Should have updated name
    assertEquals(service.withName("copy").name, "copy")

    // Should have updated type but old name
    assertTrue(
      service
        .withType(ServiceImplementationBindings.Type.HTTP)
        .type
        .equals(ServiceImplementationBindings.Type.HTTP) &&
        service.withType(ServiceImplementationBindings.Type.HTTP).name == "increment"
    )

    // Should have updated isLocal flag
    assertTrue(!(service.withLocal(false).isLocal))

    // Should have updated scheme
    assertEquals(service.withScheme("otherScheme").scheme, "otherScheme")

    // Should have updated host but old name
    assertTrue(
      service.withHost("otherHost").host.equals("otherHost") &&
        service.withHost("otherHost").name.equals("increment")
    )

    // Should have updated port
    assertEquals(service.withPort(8080).port, 8080)

    // Should have updated endpoint
    assertEquals(service.withEndPoint("/somethingElse").endPoint, "/somethingElse")

    // Should have updated HttpMethod
    assertEquals(
      service.withMethod(ServiceImplementationBindings.HttpMethod.POST).method,
      ServiceImplementationBindings.HttpMethod.POST,
    )

    // Should have old endpoint
    assertEquals(
      service.withMethod(ServiceImplementationBindings.HttpMethod.POST).endPoint,
      "/increment",
    )

    // Should be different from initial service
    val otherService = service.withName("otherService")

    // Should be equal to initial service
    val sameService = service

    // Should be the same
    assertTrue(sameService.equals(service) && service.equals(sameService))
    assertTrue(sameService.hashCode() == service.hashCode())
    assertEquals(sameService.toString(), service.toString())

    // Should be different
    assertFalse(otherService.equals(service) || service.equals(otherService))
    assertNotEquals(otherService.toString(), service.toString())
  }
}
