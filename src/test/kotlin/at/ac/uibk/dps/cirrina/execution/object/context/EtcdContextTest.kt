package at.ac.uibk.dps.cirrina.execution.`object`.context

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.provider.EtcdContext
import org.junit.jupiter.api.Assumptions.assumeTrue

class EtcdContextTest : ContextTest() {

  override fun createContext(): Context =
    System.getenv("ETCD_CONTEXT_URL").let { etcdServerUrl ->
      assumeTrue(
        etcdServerUrl != null,
        "skipping Etcd persistent context test: ETCD_CONTEXT_URL not set",
      )

      return EtcdContext(listOf(etcdServerUrl)).apply { awaitReady(10_000).getOrThrow() }
    }
}
