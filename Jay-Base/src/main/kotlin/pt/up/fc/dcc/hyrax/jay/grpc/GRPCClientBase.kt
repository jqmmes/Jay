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
        JayLogger.logInfo("CHANNEL_BUILT", actions = arrayOf("HOST=$host", "PORT=$port", "CHANNEL_STATE=${channel.getState(true).name}"))
    }

    abstract fun reconnectStubs()

    fun reconnectChannel(host: String = this.host, port: Int = this.port) {
        if (!(channel.isShutdown || channel.isTerminated)) channel.shutdownNow()
        this.port = port
        this.host = host
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(JaySettings.GRPC_MAX_MESSAGE_SIZE)
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