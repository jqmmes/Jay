package pt.up.fc.dcc.hyrax.odlib.grpc

import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import pt.up.fc.dcc.hyrax.odlib.protoc.ODCommunicationGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.utils.*
import java.util.concurrent.TimeUnit


@Suppress("unused")
class GRPCClient
    /** Construct client for accessing RouteGuide server using the existing channel.  */
internal constructor(private var channel: ManagedChannel) {
    private var blockingStub: ODCommunicationGrpc.ODCommunicationBlockingStub = ODCommunicationGrpc.newBlockingStub(channel)
    private var host: String = ""
    private var port: Int = 0


    constructor(host: String, port: Int) : this(ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()) {
        this.host = host
        this.port = port
    }

    fun reconnectChannel() {
        if(!(channel.isShutdown || channel.isTerminated)) channel.shutdownNow()
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build()
        blockingStub = ODCommunicationGrpc.newBlockingStub(channel)
    }

    fun shutdownNow() {
        channel.shutdownNow()
    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    fun putResults(id: Long, results : List<ODUtils.ODDetection?>){
        try {
            blockingStub.putResultAsync(ODUtils.genResults(id, results))
        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC failed: " + e.status)
            return
        }
        ODLogger.logInfo("RPC putResults success")
    }

    fun putJobAsync(id: Long, data: ByteArray, callback: (List<ODUtils.ODDetection?>) -> Unit) : Boolean {
        try {
            GRPCServer.addAsyncResultsCallback(id, callback)
            blockingStub.putJobAsync(ODUtils.genAsyncRequest(id, data))
            ODLogger.logInfo("RPC putJobAsync success")
            return true
        } catch (e: StatusRuntimeException) {
            GRPCServer.removeAsyncResultsCallback(id)
            ODLogger.logError("RPC failed: " + e.status)
        }
        return false
    }

    fun putJobSync(id: Int, data: ByteArray) : ODProto.JobResults? {
        try {
            val result = blockingStub.putJobSync(ODUtils.genJobRequest(id.toLong(), data))
            ODLogger.logInfo("RPC putJobSync success")
            return result

        } catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC failed: " + e.status)
        }
        return null
    }

    fun sayHello() {
        try {
            blockingStub.sayHello(ODUtils.genLocalClient())
            ODLogger.logInfo("said Hello")
        }catch (e: StatusRuntimeException){
            ODLogger.logError("Say Hello failed " + e.status)
        }
    }

    fun getModels() : Set<ODModel> {
        val result : ODProto.Models
        try {
            result = blockingStub.listModels(Empty.getDefaultInstance())
        }catch (e: StatusRuntimeException) {
            ODLogger.logError("RPC Failed: " + e.status)
            return emptySet()
        }
        return ODUtils.parseModels(result)
    }

    fun ping() : Boolean{
        try {
            blockingStub.ping(Empty.newBuilder().build())
            ODLogger.logInfo("pinged")
            return true
        }catch (e: StatusRuntimeException){
            ODLogger.logError("Error pinging " + e.status)
        }
        return false
    }
}