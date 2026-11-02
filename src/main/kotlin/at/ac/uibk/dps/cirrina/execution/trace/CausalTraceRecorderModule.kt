package at.ac.uibk.dps.cirrina.execution.trace

import at.ac.uibk.dps.cirrina.EnvironmentVariables
import at.ac.uibk.dps.cirrina.execution.trace.neo4j.Neo4JCausalTraceRecorder
import com.codahale.metrics.MetricRegistry
import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton

@Module
class CausalTraceRecorderModule {
  @Provides
  @Singleton
  fun provideTraceRecorder(metricRegistry: MetricRegistry): CausalTraceRecorder =
    when (EnvironmentVariables.traceRecorder.get()?.uppercase()) {
      "NEO4J" -> Neo4JCausalTraceRecorder.create(metricRegistry)
      null -> CausalTraceRecorder.noop
      else -> error("invalid value for environment variable TRACE_RECORDER")
    }
}
