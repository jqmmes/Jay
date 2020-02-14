package pt.up.fc.dcc.hyrax.jay.services.worker.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.jay.AbstractJay
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.ExecutionException

@Suppress("DuplicatedCode")
class WorkerGRPCClient(host: String) : GRPCClientBase<WorkerServiceGrpc.WorkerServiceBlockingStub, WorkerServiceGrpc.WorkerServiceFutureStub>
(host, JaySettings.WORKER_PORT) {
    override var blockingStub: WorkerServiceGrpc.WorkerServiceBlockingStub = WorkerServiceGrpc.newBlockingStub(channel)
    override var futureStub: WorkerServiceGrpc.WorkerServiceFutureStub = WorkerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = WorkerServiceGrpc.newBlockingStub(channel)
        futureStub = WorkerServiceGrpc.newFutureStub(channel)
    }

    fun execute(job: JayProto.WorkerJob?, callback: ((JayProto.Results?) -> Unit)? = null) {
        JayLogger.logInfo("INIT", job?.id ?: "")
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val futureJob = futureStub.execute(job)
        futureJob.addListener(Runnable {
            try {
                callback?.invoke(futureJob.get())
                JayLogger.logInfo("COMPLETE", job?.id ?: "")
            } catch (e: ExecutionException) {
                JayLogger.logWarn("ERROR", job?.id ?: "")
            }
        }, AbstractJay.executorPool)
    }

    fun listModels(callback: ((JayProto.Models) -> Unit)? = null) {
        JayLogger.logInfo("INIT")
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.listModels(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE")
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR")
            }

        }, AbstractJay.executorPool)
    }

    fun selectModel(request: JayProto.Model?, callback: ((JayProto.Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = *arrayOf("MODEL_ID=${request?.id}"))
        val call = futureStub.selectModel(request)
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_ID=${request?.id}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = *arrayOf("MODEL_ID=${request?.id}"))
                callback?.invoke(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }

    fun testService(serviceStatus: ((JayProto.ServiceStatus?) -> Unit)) {
        if (channel.getState(true) != ConnectivityState.READY) serviceStatus(null)
        val call = futureStub.testService(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                serviceStatus(call.get())
            } catch (e: Exception) {
                serviceStatus(null)
            }
        }, AbstractJay.executorPool)
    }

    fun stopService(callback: (JayProto.Status?) -> Unit) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) callback(JayUtils.genStatusError())
        val call = futureStub.stopService(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback(call.get())
            } catch (e: Exception) {
                callback(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }
}