package at.ac.uibk.dps.cirrina.runtime

import at.ac.uibk.dps.cirrina.cirrina.Runtime
import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.csm.ServiceImplementationBindings
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.execution.`object`.context.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.context.NatsContext
import at.ac.uibk.dps.cirrina.execution.`object`.event.Event
import at.ac.uibk.dps.cirrina.execution.`object`.event.NatsEventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.ContextVariableProtos
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventExchange
import at.ac.uibk.dps.cirrina.execution.`object`.exchange.EventProtos
import at.ac.uibk.dps.cirrina.execution.`object`.expression.Utility
import at.ac.uibk.dps.cirrina.execution.service.RandomServiceImplementationSelector
import at.ac.uibk.dps.cirrina.execution.service.ServiceImplementationBuilder
import at.ac.uibk.dps.cirrina.io.plantuml.CollaborativeStateMachineExporter
import at.ac.uibk.dps.cirrina.io.plantuml.PlantUmlExporter
import at.ac.uibk.dps.cirrina.utils.BuildVersion
import at.ac.uibk.dps.cirrina.utils.TestUtils.loggingOpenTelemetry
import at.ac.uibk.dps.cirrina.utils.TestUtils.mockHttpServer
import io.nats.client.*
import io.nats.client.api.KeyValueEntry
import java.io.StringWriter
import java.time.Duration
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CompleteTest {

  // Create inner subclass to test NatsEventHandler propagateEvent
  @Nested
  inner class PropagateEventTestClass(natsUrl: String) : NatsEventHandler(natsUrl) {
    var lastPropagatedEvent: Event? = null
      private set

    // Override to check if propagateEvent is called with correct event
    override fun propagateEvent(event: Event) {
      this.lastPropagatedEvent = event
      super.propagateEvent(event)
    }
  }

  // Mocks for NATS
  private lateinit var mockConnection: Connection
  private lateinit var mockKeyValueManagement: KeyValueManagement
  private lateinit var mockKeyValue: KeyValue
  private lateinit var mockNats: MockedStatic<Nats>

  private val mockStorage = mutableMapOf<String, ByteArray>()

  // Mocks for NATSEventHandler
  private lateinit var mockDispatcher: Dispatcher
  private lateinit var messageHandlerCaptor: ArgumentCaptor<MessageHandler>

  @BeforeEach
  fun setUpNatsMock() {
    // Initialize previously declared mocks
    mockConnection = mock()
    mockKeyValueManagement = mock()
    mockKeyValue = mock()
    mockDispatcher = mock()

    // Intercept static method calls to the NATS class
    mockNats = Mockito.mockStatic(Nats::class.java)
    mockNats.`when`<Connection> { Nats.connect(any<String>()) }.thenReturn(mockConnection)

    // Capture argument values for MessageHandler
    messageHandlerCaptor = ArgumentCaptor.forClass(MessageHandler::class.java)

    // Chain mocks together
    whenever(mockConnection.createDispatcher(messageHandlerCaptor.capture()))
      .thenReturn(mockDispatcher)
    whenever(mockConnection.keyValueManagement()).thenReturn(mockKeyValueManagement)
    whenever(mockConnection.keyValue(any())).thenReturn(mockKeyValue)
    whenever(mockKeyValueManagement.bucketNames).thenReturn(emptyList())

    // Store key/value in mockStorage and return 1L as revision number
    whenever(mockKeyValue.put(any(), any<ByteArray>())).then {
      mockStorage[it.arguments[0] as String] = it.arguments[1] as ByteArray
      1L
    }

    // Add key/value to mockStorage if key does not exist
    whenever(mockKeyValue.create(any(), any())).then {
      val key = it.arguments[0] as String

      if (mockStorage.contains(key)) {
        throw RuntimeException("Key $key already exists")
      }

      mockStorage[key] = it.arguments[1] as ByteArray
      1L
    }

    // Return KeyValueEntry element if value for key exists in mockStorage
    whenever(mockKeyValue.get(any())).then {
      val key = it.arguments[0] as String
      val value = mockStorage[key]

      if (value != null) {
        val mockEntry: KeyValueEntry = mock()
        whenever(mockEntry.value).thenReturn(value)
        mockEntry
      } else {
        null
      }
    }
  }

  // Teardown for NATS and storage
  @AfterEach
  fun teardownNatsMock() {
    mockNats.close()
    mockStorage.clear()
  }

  @Test
  @Order(1)
  fun testCompleteExecute() {
    val buildVersion = BuildVersion.getBuildVersion()
    assertNull(buildVersion)

    // Must finish within ten seconds
    assertTimeout(Duration.ofSeconds(100)) {
      // Should not throw any exception
      assertDoesNotThrow {
        // Instantiate NatsEventHandler through subclass
        val eventHandler = PropagateEventTestClass("nats://mock:4222")

        // Instantiate NatsContext
        val natsContext = NatsContext(false, "nats://mock:4222", "test")

        // Mock the HTTP server
        val server = mockHttpServer { input ->
          val v = input.firstOrNull { it.name == "v" } ?: error("Variable 'v' not found")

          listOf(ContextVariable("v", (v.value as Int) + 1))
        }

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

        val services = ServiceImplementationBuilder.from(listOf(service)).build()
        val serviceImplementationSelector = RandomServiceImplementationSelector(services)

        // Create and run the runtime using two state machines (stateMachine1 and stateMachine2)
        val runtime =
          Runtime(loggingOpenTelemetry(), serviceImplementationSelector, eventHandler, natsContext)
        runtime.run(DefaultDescriptions.complete, listOf("stateMachine1"))

        // Capture arguments for String class
        val subjectCaptor = ArgumentCaptor.forClass(String::class.java)

        // Should have published global event ne1 atLeastOnce
        verify(mockConnection, atLeastOnce()).publish(subjectCaptor.capture(), any())
        assertTrue(subjectCaptor.allValues.any { it.endsWith(".ne1") })

        val event =
          Event("testEvent", EventChannel.GLOBAL, listOf(ContextVariable("varName", "some string")))

        val mockMessage = mock<Message>()
        whenever(mockMessage.data).thenReturn(EventExchange(event).toBytes())

        val capturedHandler = messageHandlerCaptor.value
        capturedHandler.onMessage(mockMessage)

        // Should have propagated the testEvent
        assertNotNull(eventHandler.lastPropagatedEvent)
        assertEquals(event.name, eventHandler.lastPropagatedEvent?.getName())

        // Retrieve all state machine instances registered with the runtime
        val allStateMachines = runtime.getAllInstances().toMutableList()

        // Should be "stateMachine1"
        assertEquals(allStateMachines.first().getStateMachineClass().toString(), "stateMachine1")

        allStateMachines.removeAt(0)

        // Should contain nestedStateMachine after removing statemachine1
        assertEquals(allStateMachines.size, 1)

        // Should have state a
        assertEquals(
          allStateMachines
            .first()
            .getStateMachineClass()
            .findStateClassByName("a")
            .getOrNull()
            .toString(),
          "a",
        )

        // Should not have state b
        assertNull(
          allStateMachines.first().getStateMachineClass().findStateClassByName("b").getOrNull()
        )

        // This test counts up to 100, and down to 0, so the final value should be 0
        assertEquals(natsContext.get("v"), 0)
        assertEquals(natsContext.get("b"), true)

        server.stop(1)
      }
    }
  }

  @Test
  @Order(2)
  fun testUtility() {
    val sizes = intArrayOf(10, 50, 100, 200, 500)
    val sizeSet = HashSet<Int>()

    repeat(100) {
      val payload = Utility.genRandPayload(sizes)
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
  @Order(3)
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
  @Order(4)
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

  @Test
  @Order(5)
  fun testPlantUml() {
    // Setup mocks again
    val eventHandler = PropagateEventTestClass("nats://mock:4222")

    val natsContext = NatsContext(false, "nats://mock:4222", "test")

    val server = mockHttpServer { input ->
      val v = input.firstOrNull { it.name == "v" } ?: error("Variable 'v' not found")

      listOf(ContextVariable("v", (v.value as Int) + 1))
    }

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

    val services = ServiceImplementationBuilder.from(listOf(service)).build()
    val serviceImplementationSelector = RandomServiceImplementationSelector(services)

    // Start the runtime
    val runtime =
      Runtime(loggingOpenTelemetry(), serviceImplementationSelector, eventHandler, natsContext)
    runtime.run(DefaultDescriptions.complete, listOf("stateMachine1"))

    // Retrieve all stateMachines registered with the runtime
    val allStateMachines = runtime.getAllInstances()

    server.stop(1)

    // Create PlantUmlExporter with retrieved stateMachineClass
    val plantUmlExporter = PlantUmlExporter()
    plantUmlExporter.withStateMachine(allStateMachines.first().getStateMachineClass())

    // Create visitor to visit all stateMachines
    val export = plantUmlExporter.getPlantUml()

    // Create array of strings that the PlantUML string should contain
    val importantSubstrings =
      arrayOf(
        "stateMachine1",
        "state \"a\"",
        "state \"b\"",
        "state \"e\"",
        "[*] -->",
        "--> [*]",
        "Invoke{increment}",
        "nestedStateMachine1",
      )

    // Should contain all of the above substrings
    for (string in importantSubstrings) {
      assertTrue(export.contains(string))
    }

    // Should not throw exception when exporting
    assertDoesNotThrow {
      CollaborativeStateMachineExporter.export(
        StringWriter(),
        allStateMachines.first().getStateMachineClass(),
      )
    }
  }
}
