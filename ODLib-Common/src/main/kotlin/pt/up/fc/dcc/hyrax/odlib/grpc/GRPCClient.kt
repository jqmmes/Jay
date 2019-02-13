package pt.up.fc.dcc.hyrax.odlib.grpc

import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
//import pt.up.fc.dcc.hyrax.odlib.protoc.ODCommunicationGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.utils.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Deprecated("Will be Broker, etc")
class GRPCClient
    /** Construct client for accessing RouteGuide server using the existing channel.  */
internal constructor(private var channel: ManagedChannel) {
    //private var blockingStub: ODCommunicationGrpc.ODCommunicationBlockingStub
    //private var futureStub: ODCommunicationGrpc.ODCommunicationFutureStub = ODCommunicationGrpc.newFutureStub(channel)
    private var host: String = ""
    private var port: Int = 0
    private val threadPool = Executors.newSingleThreadExecutor()!!

    init {
        //blockingStub = ODCommunicationGrpc.newBlockingStub(channel)
    }

    constructor(host: String, port: Int) : this(ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .maxInboundMessageSize(ODSettings.grpcMaxMessageSize)
            .build()) {
        this.host = host
        this.port = port
    }

    fun reconnectChannel() {
        if(!(channel.isShutdown || channel.isTerminated)) channel.shutdownNow()
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build()
        //blockingStub = ODCommunicationGrpc.newBlockingStub(channel)
    }

    fun shutdownNow() {
        channel.shutdownNow()
    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    fun putResults(id: Long, results : List<ODDetection?>) : Boolean {
        try {
            //blockingStub.putResultAsync(ODUtils.genResults(id, results))
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC failed: " + e.status)
            return false
        }
        ODLogger.logInfo("RPC putResults success")
        return true
    }

    fun putJobAsync(id: String, data: ByteArray, callback: (List<ODDetection?>) -> Unit) : Boolean {
        try {
            //GRPCServer.addAsyncResultsCallback(id, callback)
            //blockingStub.withDeadlineAfter(ODSettings.grpcTimeout, TimeUnit.SECONDS).putJobAsync(ODUtils
            val request = ODUtils.genAsyncRequest(id, data)
            //blockingStub.putJobAsync(request)
            ODLogger.logInfo("RPC putJobAsync , success")
            return true
        } catch (e: StatusRuntimeException) {
            //GRPCServer.removeAsyncResultsCallback(id)
            ODLogger.logError("RPC failed: " + e.status)
        }
        return false
    }

    fun putJobCloudSync(id: Long, data: ByteArray) : Pair<Boolean, List<ODDetection?>> {
        try {
            //val futureJob = futureStub.putJobSync(ODUtils.genJobRequest(id, data))
            ODLogger.logInfo("RPC putJobCloudSync success")
            //return Pair(true, ODUtils.parseResults(futureJob.get()))
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC failed: " + e.status)
        }
        return Pair(false, emptyList())
    }

    fun putJobCloudAsync(id: Long, data: ByteArray, callback: (List<ODDetection?>) -> Unit) : Boolean {
        try {
            //val futureJob = futureStub.putJobSync(ODUtils.genJobRequest(id, data))
            //futureJob.addListener({ callback(ODUtils.parseResults(futureJob.get())) }, { R -> threadPool.submit(R) })
            ODLogger.logInfo("RPC putJobCloudAsync success")
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC failed: " + e.status)
            return false
        }
        return true
    }

    fun putJobSync(id: Long, data: ByteArray) : ODProto.Results? {
        try {
            //val result = blockingStub.putJobSync(ODUtils.genJobRequest(id, data))
            ODLogger.logInfo("RPC putJobSync success")
            return null
            //return result
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC failed: " + e.status)
        }
        return null
    }

    fun getModels() : Pair<Boolean, Set<ODModel>> {
        val result : ODProto.Models
        try {
            //result = blockingStub.withDeadlineAfter(ODSettings.grpcTimeout, TimeUnit.SECONDS).listModels(Empty.getDefaultInstance())
        }catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC Failed: " + e.status)
            return Pair(false, emptySet())
        }
        return Pair(false, emptySet())
        //return Pair(true, ODUtils.parseModels(result))
    }

    fun selectModel(model: ODModel, block: Boolean = false): Boolean {
        ODLogger.logInfo("Selecting Model")
        try {
            //if (block) blockingStub.selectModel(ODUtils.genModel(model))
            //else thread {
                //blockingStub.selectModel(ODUtils.genModel(model))
            //}
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC Failed: " + e.status)
            return false
        }
        ODLogger.logInfo("Model Selected")
        return true
    }

    fun sayHello() : Boolean {
        try {
            //blockingStub.sayHello(ODUtils.genLocalClient())
            ODLogger.logInfo("said Hello")
        }catch (e: StatusRuntimeException){
            ODLogger.logError("Say Hello failed " + e.status)
            return false
        }
        return true
    }

    fun ping() : Boolean {
        try {
            //.withDeadlineAfter(ODSettings.grpcShortTimeout, TimeUnit.MILLISECONDS)
            //blockingStub.ping(Empty.newBuilder().build())
            ODLogger.logInfo("pinged")
            return true
        } catch (e: StatusRuntimeException){
            ODLogger.logError("Error pinging " + e.status)
        }
        return false
    }

    fun configureModel(genModelConfig: ODProto.ModelConfig) {
        try {
            //blockingStub.configModel(genModelConfig)
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC Failed: " + e.status)
        }
    }

    fun modelLoaded(model: ODModel): Boolean {
        try {
            //return blockingStub.modelLoaded(ODUtils.genModel(model)).value
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC Failed: " + e.status)
        }
        return false
    }

    fun getStatus() : DeviceInformation? {
        try {
            //val result = blockingStub.getStatus(Empty.newBuilder().build())
            //return ODUtils.parseDeviceStatus(result)
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RªC Failed: " + e.status)
        }
        return null
    }
}