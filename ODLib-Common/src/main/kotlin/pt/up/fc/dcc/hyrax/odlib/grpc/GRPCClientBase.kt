package pt.up.fc.dcc.hyrax.odlib.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.TimeUnit

abstract class GRPCClientBase<T1, T2>(private val host: String, private val port: Int) {
    /** Construct client for accessing RouteGuide server using the existing channel.  */
    var channel: ManagedChannel
    abstract var blockingStub: T1
    abstract var futureStub: T2

    init {
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(ODSettings.GRPC_MAX_MESSAGE_SIZE)
                .build()
        ODLogger.logInfo("CHANNEL_BUILT", actions = *arrayOf("HOST=$host", "PORT=$port", "CHANNEL_STATE=${channel.getState(true).name}"))
    }

    abstract fun reconnectStubs()

    fun reconnectChannel() {
        if (!(channel.isShutdown || channel.isTerminated)) channel.shutdownNow()
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