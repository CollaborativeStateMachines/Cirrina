package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.execution.`object`.EventHandler
import at.ac.uibk.dps.cirrina.execution.`object`.EventHandlerTest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventHandlerZenohTest : EventHandlerTest() {
  override fun createEventHandler(): EventHandler = EventHandlerZenoh()

  @Test
  fun testRegisterWait() = runBlocking {
    val parties = 10
    val group = "group"
    val completedParties = AtomicInteger(0)

    val jobs =
      (1..parties).map { i ->
        launch(Dispatchers.IO) {
          createEventHandler().use { handler ->
            val memberName = "member-$i"

            if (i > parties / 2) delay(100)

            handler.register(group, memberName)
            handler.wait(group, parties)

            completedParties.incrementAndGet()
          }
        }
      }

    withTimeout(35000) { jobs.joinAll() }

    assertEquals(parties, completedParties.get())
  }
}
