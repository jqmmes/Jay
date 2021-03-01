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

package pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.jay.AbstractJay
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayThreadPoolExecutor
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.ExecutionException

@Suppress("DuplicatedCode")
class SchedulerGRPCClient(private val host: String) : GRPCClientBase<SchedulerServiceGrpc.SchedulerServiceBlockingStub,
        SchedulerServiceGrpc.SchedulerServiceFutureStub>(host, JaySettings.SCHEDULER_PORT) {
    override var blockingStub: SchedulerServiceGrpc.SchedulerServiceBlockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
    override var futureStub: SchedulerServiceGrpc.SchedulerServiceFutureStub = SchedulerServiceGrpc.newFutureStub(channel)
    private val notifyPool: JayThreadPoolExecutor = JayThreadPoolExecutor(10)

    private fun checkConnection() {
        if (this.port != JaySettings.SCHEDULER_PORT) {
            reconnectChannel(host, JaySettings.SCHEDULER_PORT)
        }
    }

    override fun reconnectStubs() {
        blockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
        futureStub = SchedulerServiceGrpc.newFutureStub(channel)
    }

    fun schedule(request: JayProto.TaskInfo?, callback: ((JayProto.Worker?) -> Unit)? = null) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        callback?.invoke(blockingStub.schedule(request))
    }

    fun notifyTaskComplete(request: JayProto.TaskInfo?) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return
        }
        notifyPool.submit {
            JayLogger.logInfo("WILL_NOTIFY_SCHEDULER", request?.id ?: "", "BEGIN")
            blockingStub.notifyTaskComplete(request)
            JayLogger.logInfo("NOTIFIED_SCHEDULER", request?.id ?: "", "END")
        }
    }

    fun listSchedulers(callback: ((JayProto.Schedulers?) -> Unit)? = null) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.listSchedulers(Empty.getDefaultInstance())
        call.addListener({
            try {
                callback?.invoke(call.get())
            } catch (e: ExecutionException) {
                JayLogger.logError("UNAVAILABLE")
            }
        }, AbstractJay.executorPool)
    }

    fun setScheduler(request: JayProto.Scheduler?, callback: ((JayProto.Status?) -> Unit)? = null) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setScheduler(request)
        call.addListener({ callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    fun setSchedulerSettings(request: JayProto.Settings?, callback: ((JayProto.Status?) -> Unit)?) {
        checkConnection()
        JayLogger.logInfo("INIT", actions = arrayOf("SETTINGS=${request?.settingMap?.keys}"))
        val call = futureStub.setSchedulerSettings(request)
        call.addListener({
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = arrayOf("SETTINGS=${request?.settingMap?.keys}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = arrayOf("SETTINGS=${request?.settingMap?.keys}"))
                callback?.invoke(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }

    fun notifyWorkerUpdate(request: JayProto.Worker?, callback: ((JayProto.Status?) -> Unit)) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return callback(JayUtils.genStatus(JayProto.StatusCode.Error))
        }
        val call = futureStub.notifyWorkerUpdate(request)
        call.addListener({
            try {
                callback(call.get())
            } catch (e: ExecutionException) {
                callback(JayUtils.genStatus(JayProto.StatusCode.Error))
            }
        }, AbstractJay.executorPool)
    }

    fun notifyWorkerFailure(request: JayProto.Worker?, callback: ((JayProto.Status?) -> Unit)) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return callback(JayUtils.genStatus(JayProto.StatusCode.Error))
        }
        val call = futureStub.notifyWorkerFailure(request)
        call.addListener({
            try {
                callback(call.get())
            } catch (e: ExecutionException) {
                callback(JayUtils.genStatus(JayProto.StatusCode.Error))
            }
        }, AbstractJay.executorPool)
    }

    fun testService(serviceStatus: ((JayProto.ServiceStatus?) -> Unit)) {
        checkConnection()
        if (channel.getState(true) != ConnectivityState.READY) serviceStatus(null)
        val call = futureStub.testService(Empty.getDefaultInstance())
        call.addListener({
            try {
                serviceStatus(call.get())
            } catch (e: Exception) {
                serviceStatus(null)
            }
        }, AbstractJay.executorPool)
    }

    fun stopService(callback: (JayProto.Status?) -> Unit) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) callback(JayUtils.genStatusError())
        val call = futureStub.stopService(Empty.getDefaultInstance())
        call.addListener({
            try {
                callback(call.get())
            } catch (e: Exception) {
                callback(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }
}