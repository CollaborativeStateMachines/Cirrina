package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.provider.ContextEtcd
import org.junit.jupiter.api.Assumptions.assumeTrue

class EtcdContextTest : ContextTest() {

  override fun createContext(): Context =
    System.getenv("ETCD_CONTEXT_URL").let { etcdServerUrl ->
      assumeTrue(
        etcdServerUrl != null,
        "skipping Etcd persistent context test: ETCD_CONTEXT_URL not set",
      )

      return ContextEtcd(listOf(etcdServerUrl)).apply { awaitReady(10_000).getOrThrow() }
    }
}
