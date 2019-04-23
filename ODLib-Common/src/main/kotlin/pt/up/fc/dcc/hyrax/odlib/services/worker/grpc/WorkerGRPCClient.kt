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
import io.grpc.ConnectivityState

class WorkerGRPCClient(host: String) : GRPCClientBase<WorkerServiceGrpc.WorkerServiceBlockingStub, WorkerServiceGrpc.WorkerServiceFutureStub>
(host, ODSettings.workerPort) {
    override var blockingStub: WorkerServiceGrpc.WorkerServiceBlockingStub = WorkerServiceGrpc.newBlockingStub(channel)
    override var futureStub: WorkerServiceGrpc.WorkerServiceFutureStub = WorkerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = WorkerServiceGrpc.newBlockingStub(channel)
        futureStub = WorkerServiceGrpc.newFutureStub(channel)
    }

    fun execute(job: ODProto.Job?, callback: ((ODProto.Results?) -> Unit)? = null) {
        ODLogger.logInfo("INIT", job?.id ?: "")
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val futureJob = futureStub.execute(job)
        futureJob.addListener(Runnable {
            try {
                callback?.invoke(futureJob.get())
                ODLogger.logInfo("COMPLETE", job?.id ?: "")
            } catch (e: ExecutionException) {
                ODLogger.logWarn("ERROR", job?.id ?: "")
            }
        }, AbstractODLib.executorPool)
    }

    fun listModels(callback: ((ODProto.Models) -> Unit)? = null) {
        ODLogger.logInfo("INIT")
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.listModels(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                ODLogger.logInfo("COMPLETE")
            } catch (e: ExecutionException) {
                ODLogger.logInfo("ERROR")
            }

        }, AbstractODLib.executorPool)
    }

    fun selectModel(request: ODProto.Model?, callback: ((ODProto.Status?) -> Unit)?) {
        ODLogger.logInfo("INIT", actions = *arrayOf("MODEL_ID=${request?.id}"))
        val call = futureStub.selectModel(request)
        call.addListener(Runnable { try {callback?.invoke(call.get())
            ODLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_ID=${request?.id}"))
        } catch (e: ExecutionException) {
            ODLogger.logInfo("ERROR", actions = *arrayOf("MODEL_ID=${request?.id}"))
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