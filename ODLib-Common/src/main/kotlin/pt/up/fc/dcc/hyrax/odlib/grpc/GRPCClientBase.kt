package pt.up.fc.dcc.hyrax.odlib.grpc

import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class GRPCClientBase<T1, T2>(private val host: String, private val port: Int) {
    /** Construct client for accessing RouteGuide server using the existing channel.  */
    var channel: ManagedChannel
    abstract var blockingStub: T1
    abstract var futureStub: T2

    init {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(ODSettings.grpcMaxMessageSize)
                .build()
        println("Channel Built $host:$port\tState:${channel.getState(true).name}")
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