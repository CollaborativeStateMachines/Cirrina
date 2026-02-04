package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.ContextTest
import org.junit.jupiter.api.Assumptions.assumeTrue

class ContextEtcdTest : ContextTest() {
  override fun createContext(): Context =
    System.getenv("ETCD_CONTEXT_URL").let { etcdServerUrl ->
      assumeTrue(
        etcdServerUrl != null,
        "skipping etcd persistent context test: ETCD_CONTEXT_URL not set",
      )

      return ContextEtcd(listOf(etcdServerUrl))
    }
}
