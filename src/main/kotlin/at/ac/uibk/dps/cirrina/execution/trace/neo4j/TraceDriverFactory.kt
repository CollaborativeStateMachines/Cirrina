package at.ac.uibk.dps.cirrina.execution.trace.neo4j

import com.codahale.metrics.Meter
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import java.net.URI
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.neo4j.bolt.connection.BoltConnectionProvider
import org.neo4j.bolt.connection.LoggingProvider
import org.neo4j.driver.AuthToken
import org.neo4j.driver.Config
import org.neo4j.driver.Driver
import org.neo4j.driver.internal.DriverFactory
import org.neo4j.driver.internal.security.StaticAuthTokenManager

internal class TraceDriverFactory(private val sentBytes: Meter) : DriverFactory() {
  fun create(uri: String, auth: AuthToken, config: Config): Driver =
    newInstance(URI.create(uri), StaticAuthTokenManager(auth), null, config)

  override fun createBoltConnectionProvider(
    executor: ScheduledExecutorService?,
    clock: Clock,
    logging: LoggingProvider,
    eventLoopThreads: Int,
  ): BoltConnectionProvider {
    val group =
      object :
        MultiThreadIoEventLoopGroup(eventLoopThreads.coerceAtLeast(0), NioIoHandler.newFactory()) {
        override fun register(channel: Channel): ChannelFuture {
          channel
            .pipeline()
            .addLast(
              object : ChannelInitializer<Channel>() {
                override fun initChannel(channel: Channel) {
                  channel.pipeline().addFirst(TraceByteCounter(sentBytes))
                }
              }
            )
          return super.register(channel)
        }
      }

    val provider =
      try {
        super.createBoltConnectionProvider(group, clock, logging, eventLoopThreads)
      } catch (failure: Throwable) {
        group.shutdownGracefully(0, 2, TimeUnit.SECONDS)
        throw failure
      }

    return object : BoltConnectionProvider by provider {
      override fun close(): CompletionStage<Void> {
        val closed = CompletableFuture<Void>()
        provider.close().whenComplete { _, failure ->
          group.shutdownGracefully(0, 2, TimeUnit.SECONDS).addListener { stopped ->
            val error = failure ?: stopped.cause()
            if (error == null) closed.complete(null) else closed.completeExceptionally(error)
          }
        }
        return closed
      }
    }
  }
}

internal class TraceByteCounter(private val sentBytes: Meter) : ChannelOutboundHandlerAdapter() {
  override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
    if (message is ByteBuf) {
      val bytes = message.readableBytes().toLong()
      val completion = promise.unvoid()

      completion.addListener { if (it.isSuccess) sentBytes.mark(bytes) }

      context.write(message, completion)
    } else {
      context.write(message, promise)
    }
  }
}
