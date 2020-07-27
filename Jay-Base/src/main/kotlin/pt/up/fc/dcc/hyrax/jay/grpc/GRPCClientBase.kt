package pt.up.fc.dcc.hyrax.jay.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import java.util.concurrent.TimeUnit

abstract class GRPCClientBase<T1, T2>(private var host: String, var port: Int) {
    /** Construct client for accessing RouteGuide server using the existing channel.  */
    var channel: ManagedChannel
    abstract var blockingStub: T1
    abstract var futureStub: T2

    init {
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(JaySettings.GRPC_MAX_MESSAGE_SIZE)
                .build()
        JayLogger.logInfo("CHANNEL_BUILT", actions = *arrayOf("HOST=$host", "PORT=$port", "CHANNEL_STATE=${channel.getState(true).name}"))
    }

    abstract fun reconnectStubs()

    fun reconnectChannel(host: String = this.host, port: Int = this.port) {
        if (!(channel.isShutdown || channel.isTerminated)) channel.shutdownNow()
        this.port = port
        this.host = host
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build()
        reconnectStubs()
    }

    fun shutdownNow() {
        channel.shutdownNow()
    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}