package at.ac.uibk.dps.cirrina.execution.`object`.context

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.assertDoesNotThrow

class EtcdContextTest : ContextTest() {
  override fun createContext(): Context {
    val etcdServerURL = System.getenv("ETCD_CONTEXT_URL")

    assumeFalse(etcdServerURL == null, "Skipping Etcd persistent context test")

    return assertDoesNotThrow {
      EtcdContext(true, listOf(etcdServerURL)).apply { awaitReady(10000) }
    }
  }
}
