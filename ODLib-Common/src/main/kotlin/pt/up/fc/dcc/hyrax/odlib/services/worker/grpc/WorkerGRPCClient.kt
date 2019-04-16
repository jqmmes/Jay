package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import com.google.protobuf.Empty
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.ExecutionException

class WorkerGRPCClient(host: String) : GRPCClientBase<WorkerServiceGrpc.WorkerServiceBlockingStub, WorkerServiceGrpc.WorkerServiceFutureStub>
(host, ODSettings.workerPort) {
    override var blockingStub: WorkerServiceGrpc.WorkerServiceBlockingStub = WorkerServiceGrpc.newBlockingStub(channel)
    override var futureStub: WorkerServiceGrpc.WorkerServiceFutureStub = WorkerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = WorkerServiceGrpc.newBlockingStub(channel)
        futureStub = WorkerServiceGrpc.newFutureStub(channel)
    }

    fun execute(job: ODProto.Job?, callback: ((ODProto.Results?) -> Unit)? = null) {
        ODLogger.logInfo("WorkerGRPCClient, EXECUTE, START, JOB_ID=${job?.id}")
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val futureJob = futureStub.execute(job)
        futureJob.addListener(Runnable {
            try {
                callback?.invoke(futureJob.get())
                ODLogger.logInfo("WorkerGRPCClient, EXECUTE, COMPLETE, JOB_ID=${job?.id}")
            } catch (e: ExecutionException) {
                ODLogger.logWarn("Execution (id: ${job?.id} canceled")
                ODLogger.logInfo("WorkerGRPCClient, EXECUTE, ERROR, JOB_ID=${job?.id}")
            }
        }, AbstractODLib.executorPool)
    }

    fun listModels(callback: ((ODProto.Models) -> Unit)? = null) {
        ODLogger.logInfo("WorkerGRPCClient, LIST_MODELS, START")
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.listModels(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                ODLogger.logInfo("WorkerGRPCClient, LIST_MODELS, COMPLETE")
            } catch (e: ExecutionException) {
                ODLogger.logInfo("WorkerGRPCClient, LIST_MODELS, ERROR")
            }

        }, AbstractODLib.executorPool)
    }

    fun selectModel(request: ODProto.Model?, callback: ((ODProto.Status?) -> Unit)?) {
        ODLogger.logInfo("WorkerGRPCClient, SELECT_MODEL, START")
        val call = futureStub.selectModel(request)
        call.addListener(Runnable { try {callback?.invoke(call.get())
            ODLogger.logInfo("WorkerGRPCClient, SELECT_MODEL, COMPLETE")
        } catch (e: ExecutionException) {
            ODLogger.logInfo("WorkerGRPCClient, SELECT_MODEL, ERROR")
            callback?.invoke(ODUtils.genStatusError())}
        }, AbstractODLib.executorPool)
    }

    fun testService(serviceStatus: ((ODProto.ServiceStatus?) -> Unit)) {
        if (channel.getState(true) != ConnectivityState.READY) serviceStatus(null)
        val call = futureStub.testService(Empty.getDefaultInstance())
        call.addListener(Runnable { try {serviceStatus(call.get())} catch (e: Exception) {serviceStatus(null)}}, AbstractODLib.executorPool)
    }

    fun stopService(callback: (ODProto.Status?) -> Unit) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) callback(ODUtils.genStatusError())
        val call = futureStub.stopService(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback(call.get())
            } catch (e: Exception) {
                callback(ODUtils.genStatusError
                ())
            }
        },
                AbstractODLib.executorPool)
    }
}