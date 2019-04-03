package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.StatusRuntimeException
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.ODJob
import pt.up.fc.dcc.hyrax.odlib.structures.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BrokerGRPCClient(host: String) : GRPCClientBase<BrokerServiceGrpc.BrokerServiceBlockingStub, BrokerServiceGrpc.BrokerServiceFutureStub>
(host, ODSettings.brokerPort) {
    override var blockingStub: BrokerServiceGrpc.BrokerServiceBlockingStub = BrokerServiceGrpc.newBlockingStub(channel)
    override var futureStub: BrokerServiceGrpc.BrokerServiceFutureStub = BrokerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = BrokerServiceGrpc.newBlockingStub(channel)
        futureStub = BrokerServiceGrpc.newFutureStub(channel)
    }

    fun scheduleJob(job: ODJob, callback: ((ODProto.Results) -> Unit)? = null) {
        val call = futureStub.scheduleJob(job.getProto())
        call.addListener(Runnable{ callback?.invoke(call.get()) }, AbstractODLib.executorPool)//{ J -> AbstractODLib.put(J) })
    }

    fun executeJob(job: ODProto.Job?, callback: ((ODProto.Results) -> Unit)? = null) {
        val call = futureStub.executeJob(job)
        call.addListener(Runnable{ println(callback?.invoke(call.get()))}, AbstractODLib.executorPool)
    }

    fun ping(payload: Int = ODSettings.pingPayloadSize, reply: Boolean = false, timeout: Long = 15000, callback: ((Int) -> Unit)? = null) {
        AbstractODLib.executorPool.submit {
            val timer = System.currentTimeMillis()
            try {
                @Suppress("UNUSED_VARIABLE") val result = blockingStub
                        .withDeadlineAfter(timeout, TimeUnit.MILLISECONDS)
                        .ping(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(payload))).setReply(reply).build())
                callback?.invoke((System.currentTimeMillis() - timer).toInt())
            } catch (e: TimeoutException) {
                callback?.invoke(-1)
            }
        }
    }

    fun updateWorkers(callback: (() -> Unit)) {
        val call = futureStub.updateWorkers(Empty.getDefaultInstance())
        call.addListener(Runnable{ call.get(); callback() }, AbstractODLib.executorPool)
    }

    fun selectModel(model: ODModel, callback: ((ODProto.Status) -> Unit)? = null) {
        val call = futureStub.setModel(model.getProto())
        call.addListener(Runnable{ callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun getModels(callback: ((Set<ODModel>) -> Unit)? = null) {
        val call = futureStub.getModels(Empty.getDefaultInstance())
        call.addListener(Runnable{ try { callback?.invoke(ODUtils.parseModels(call.get())) }
        catch (e: ExecutionException) {
            println("getModels Unavailable")
        }
        }, AbstractODLib.executorPool)
    }

    fun getSchedulers(callback: ((Set<Pair<String, String>>) -> Unit)? = null) {
        val call = futureStub.getSchedulers(Empty.getDefaultInstance())
        call.addListener(Runnable{
            try { callback?.invoke(ODUtils.parseSchedulers(call.get())) }
            catch (e: ExecutionException) {
                println("getSchedulers Unavailable")
            }
        }, AbstractODLib.executorPool)
    }

    fun setScheduler(id: String, callback: ((Boolean) -> Unit)) {
        val call = futureStub.setScheduler(ODProto.Scheduler.newBuilder().setId(id).build())
        call.addListener(Runnable { callback(call.get().code == ODProto.StatusCode.Success) }, AbstractODLib.executorPool)
    }

    fun advertiseWorkerStatus(request: ODProto.Worker?, completeCallback: () -> Unit) {
        val call = futureStub.advertiseWorkerStatus(request)
        call.addListener(Runnable { completeCallback() }, AbstractODLib.executorPool)
    }

    fun diffuseWorkerStatus(request: ODProto.Worker?) {
        val call = futureStub.diffuseWorkerStatus(request)
        call.addListener(Runnable { println("diffuseWorkerStatus Status: ${call.get().code.name}") }, AbstractODLib.executorPool)
    }

    fun requestWorkerStatus(callback: ((ODProto.Worker?) -> Unit)){
        val call = futureStub.requestWorkerStatus(Empty.getDefaultInstance())
        call.addListener(Runnable { try {callback(call.get())} catch (e: ExecutionException) {callback(null) } }, AbstractODLib.executorPool)
    }


    fun listenMulticastWorkers(stopListener: Boolean = false, callback: ((ODProto.Status) -> Unit)? = null) {
        val call = futureStub.listenMulticast(BoolValue.of(stopListener))
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun announceMulticast() {
        val call = futureStub.announceMulticast(Empty.getDefaultInstance())
        call.addListener(Runnable { println("announceMulticast Status: ${call.get().code.name}") }, AbstractODLib.executorPool)
    }
}