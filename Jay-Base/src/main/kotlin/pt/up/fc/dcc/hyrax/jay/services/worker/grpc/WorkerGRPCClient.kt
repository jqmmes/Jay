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

package pt.up.fc.dcc.hyrax.jay.services.worker.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.jay.AbstractJay
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException

@Suppress("DuplicatedCode")
class WorkerGRPCClient(private val host: String) : GRPCClientBase<WorkerServiceGrpc.WorkerServiceBlockingStub,
        WorkerServiceGrpc.WorkerServiceFutureStub>(host, JaySettings.WORKER_PORT) {
    override var blockingStub: WorkerServiceGrpc.WorkerServiceBlockingStub = WorkerServiceGrpc.newBlockingStub(channel)
    override var futureStub: WorkerServiceGrpc.WorkerServiceFutureStub = WorkerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = WorkerServiceGrpc.newBlockingStub(channel)
        futureStub = WorkerServiceGrpc.newFutureStub(channel)
    }

    private fun checkConnection() {
        if (this.port != JaySettings.WORKER_PORT) {
            reconnectChannel(host, JaySettings.WORKER_PORT)
        }
    }

    fun execute(task: JayProto.TaskInfo?, callback: ((JayProto.Response?) -> Unit)? = null) {
        checkConnection()
        JayLogger.logInfo("INIT", task?.id ?: "")
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val countDownLatch = CountDownLatch(1)
        val futureTask = futureStub.execute(task)
        futureTask.addListener({
            try {
                val p1 = futureTask.get()
                callback?.invoke(p1)
                JayLogger.logInfo("COMPLETE", task?.id ?: "")
                countDownLatch.countDown()
            } catch (e: ExecutionException) {
                JayLogger.logWarn("ERROR", task?.id ?: "")
                countDownLatch.countDown()
            }
        }, AbstractJay.executorPool)
        countDownLatch.await()
    }

    fun callExecutorAction(request: JayProto.Request?, callback: ((JayProto.Response?) -> Unit)?) {
        checkConnection()
        JayLogger.logInfo("INIT", actions = arrayOf("ACTION=${request?.request}"))
        val call = futureStub.callExecutorAction(request)
        call.addListener({
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = arrayOf("ACTION=${request?.request}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = arrayOf("ACTION=${request?.request}"))
                callback?.invoke(JayProto.Response.newBuilder().setStatus(JayUtils.genStatusError()).build())
            }
        }, AbstractJay.executorPool)
    }

    fun runExecutorAction(request: JayProto.Request?, callback: ((JayProto.Status?) -> Unit)?) {
        checkConnection()
        JayLogger.logInfo("INIT", actions = arrayOf("ACTION=${request?.request}"))
        val call = futureStub.runExecutorAction(request)
        call.addListener({
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = arrayOf("ACTION=${request?.request}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = arrayOf("ACTION=${request?.request}"))
                callback?.invoke(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }

    fun listTaskExecutors(request: Empty?, callback: ((JayProto.TaskExecutors) -> Unit)?) {
        checkConnection()
        JayLogger.logInfo("INIT")
        val call = futureStub.listTaskExecutors(request)
        call.addListener({
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE")
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR")
                callback?.invoke(JayProto.TaskExecutors.getDefaultInstance())
            }
        }, AbstractJay.executorPool)
    }

    fun selectTaskExecutor(request: JayProto.TaskExecutor?, callback: ((JayProto.Status?) -> Unit)?) {
        checkConnection()
        JayLogger.logInfo("INIT", actions = arrayOf("ACTION=${request?.id}"))
        val call = futureStub.selectTaskExecutor(request)
        call.addListener({
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = arrayOf("ACTION=${request?.id}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = arrayOf("ACTION=${request?.id}"))
                callback?.invoke(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }

    fun setExecutorSettings(request: JayProto.Settings?, callback: ((JayProto.Status?) -> Unit)?) {
        checkConnection()
        JayLogger.logInfo("INIT", actions = arrayOf("SETTINGS=${request?.settingMap?.keys}"))
        val call = futureStub.setExecutorSettings(request)
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
                JayLogger.logInfo("COMPLETE")
                callback(call.get())
            } catch (e: Exception) {
                JayLogger.logInfo("ERROR")
                callback(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }

    fun getWorkerStatus(callback: ((JayProto.WorkerComputeStatus?) -> Unit)?) {
        checkConnection()
        JayLogger.logInfo("INIT")
        val call = futureStub.getWorkerStatus(Empty.getDefaultInstance())
        call.addListener({
            try {
                JayLogger.logInfo("COMPLETE")
                callback?.invoke(call.get())
            } catch (e: Exception) {
                JayLogger.logInfo("ERROR")
                callback?.invoke(null)
            }
        }, AbstractJay.executorPool)
    }

    fun informAllocatedTask(request: JayProto.String?, callback: ((JayProto.Status?) -> Unit)) {
        val call = futureStub.informAllocatedTask(request)
        call.addListener({
            try {
                JayLogger.logInfo("COMPLETE")
                callback.invoke(call.get())
            } catch (e: Exception) {
                JayLogger.logInfo("ERROR")
                callback(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }
}