package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
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

    fun scheduleJob(job: ODJob, callback: ((ODProto.JobResults) -> Unit)? = null) {
        val call = futureStub.scheduleJob(ODUtils.genJobRequest(job))
        call.addListener({ callback?.invoke(call.get()) }, { J -> AbstractODLib.put(J) })
    }

    fun executeJob(job: ODProto.Job?, callback: ((ODProto.JobResults) -> Unit)? = null) {
        val call = futureStub.executeJob(job)
        call.addListener({ callback?.invoke(call.get()) }, {J -> AbstractODLib.put(J)})
    }

    fun ping(payload: Int = ODSettings.pingPayloadSize, reply: Boolean = false, timeout: Long = 15000, callback: ((Long) -> Unit)? = null) {
        AbstractODLib.put(
                Runnable {
                    val timer = System.currentTimeMillis()
                    try {
                        blockingStub
                                .withDeadlineAfter(timeout, TimeUnit.MILLISECONDS)
                                .ping(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(payload))).setReply(reply).build())
                        callback?.invoke(System.currentTimeMillis() - timer)
                    } catch (e: TimeoutException) {
                        callback?.invoke(-1)
                    }


                }
        )
    }

    fun advertiseWorkerStatus(request: ODProto.Worker?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun updateWorkers() {
        BrokerService.updateWorkers()
    }

    fun selectModel(model: ODModel) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun getModels(onlyLoaded: Boolean, b: Boolean): Set<ODModel> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}