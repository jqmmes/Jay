package pt.up.fc.dcc.hyrax.odlib.grpc

import io.grpc.BindableService
import io.grpc.Context
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.io.IOException

abstract class GRPCServerBase(private val port: Int,
                              private val useNettyServer: Boolean) {
    private var server: Server? = null

    abstract val grpcImpl: BindableService

    @Throws(IOException::class)
    fun start(): GRPCServerBase {
        ODLogger.logInfo("will start server on port $port")
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        server =
                if (useNettyServer) NettyServerBuilder.forPort(port)
                    .addService(grpcImpl)
                    .maxInboundMessageSize(ODSettings.grpcMaxMessageSize)
                    .build()
                    .start()
                else ServerBuilder.forPort(port)
                    .addService(grpcImpl)
                    .maxInboundMessageSize(ODSettings.grpcMaxMessageSize)
                    .build()
                    .start()

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                ODLogger.logError("*** shutting down gRPC server since JVM is shutting down")
                this@GRPCServerBase.stop()
                ODLogger.logError("*** server shut down")
            }
        })
        return this
    }

    fun stop() {
        server?.shutdown()
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    @Suppress("unused")
    @Throws(InterruptedException::class)
    private fun blockUntilShutdown() {
        server?.awaitTermination()
    }

    protected open fun <T> genericComplete(request: T?, responseObserver: StreamObserver<T>?) {
        if (!Context.current().isCancelled) {
            responseObserver!!.onNext(request)
            responseObserver.onCompleted()
        } else {
            ODLogger.logError("GRPCServer context canceled")
        }
    }
}