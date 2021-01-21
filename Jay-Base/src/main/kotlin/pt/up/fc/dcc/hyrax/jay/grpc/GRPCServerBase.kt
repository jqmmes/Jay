/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.grpc

import io.grpc.*
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import java.io.IOException

abstract class GRPCServerBase(private val port: Int,
                              private val useNettyServer: Boolean) {
    private var server: Server? = null

    abstract val grpcImpl: BindableService

    @Throws(IOException::class)
    fun start(): GRPCServerBase? {
        JayLogger.logInfo("INIT", actions = arrayOf("PORT=$port", "USING_NETTY=$useNettyServer"))
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        try {
            server =
                    if (useNettyServer) NettyServerBuilder.forPort(port)
                            .addService(grpcImpl)
                            .maxInboundMessageSize(JaySettings.GRPC_MAX_MESSAGE_SIZE)
                            .build()
                            .start()
                    else ServerBuilder.forPort(port)
                            .addService(grpcImpl)
                            .maxInboundMessageSize(JaySettings.GRPC_MAX_MESSAGE_SIZE)
                            .build()
                            .start()
        } catch (ignore: Exception) {
            ignore.printStackTrace()
            return null
        }

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {

                JayLogger.logError("ERROR", actions = arrayOf("ERROR='*** shutting down gRPC server since JVM is shutting down'"))
                this@GRPCServerBase.stop()
                JayLogger.logError("ERROR", actions = arrayOf("'*** server shut down'"))
            }
        })
        return this
    }

    fun stop() {
        server?.shutdown()
    }

    fun stopNowAndWait() {
        server?.shutdownNow()
        server?.awaitTermination()
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
            try {
                responseObserver!!.onNext(request)
                responseObserver.onCompleted()
            } catch (e: StatusRuntimeException) {
                JayLogger.logError("CONTEXT_CANCELED")
            } catch (e: IllegalStateException) {
                JayLogger.logError("CONTEXT_CANCELED")
            }
        } else {
            JayLogger.logError("CONTEXT_CANCELED")
        }
    }
}