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
  override fun createEventHandler(group: String, member: String): EventHandler =
    EventHandlerZenoh(group, member)

  @Test
  fun testRegisterWait() = runBlocking {
    val parties = 10
    val completedParties = AtomicInteger(0)

    val jobs =
      (1..parties).map { i ->
        launch(Dispatchers.IO) {
          createEventHandler("group", "member-$i").use { handler ->
            if (i > parties / 2) delay(100)

            handler.waitForParties(parties)

            completedParties.incrementAndGet()
          }
        }
      }

    withTimeout(35000) { jobs.joinAll() }

    assertEquals(parties, completedParties.get())
  }
}
