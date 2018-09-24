package pt.up.fc.dcc.hyrax.odlib.grpc

import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import pt.up.fc.dcc.hyrax.odlib.ODClient
import pt.up.fc.dcc.hyrax.odlib.ODModel
import pt.up.fc.dcc.hyrax.odlib.ODUtils
import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.protoc.ODCommunicationGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import java.util.concurrent.TimeUnit


class GRPCClient
    /** Construct client for accessing RouteGuide server using the existing channel.  */
internal constructor(private val channel: ManagedChannel) {
    private val blockingStub: ODCommunicationGrpc.ODCommunicationBlockingStub = ODCommunicationGrpc.newBlockingStub(channel)


    /** Construct client connecting to HelloWorld server at `host:port`.  */
    constructor(host: String, port: Int) : this(ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build())

    @Throws(InterruptedException::class)
    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    fun putResults(id: Int, results : List<ODUtils.ODDetection?>){
        try {
            blockingStub.putResultAsync(ODUtils.genResults(id, results))
        } catch (e: StatusRuntimeException) {
            println("RPC failed: " + e.status)
            return
        }
        println("RPC putResults success")
    }

    fun putJobAsync(id: Int, data: ByteArray, remoteClient: ODClient) {
        try {
            blockingStub.putJobAsync(ODUtils.genAsyncRequest(id, data, remoteClient))
        } catch (e: StatusRuntimeException) {
            println("RPC failed: " + e.status)
            return
        }
        println("RPC putJobAsync success")
    }

    fun putJobSync(id: Int, data: ByteArray) : ODProto.Results? {
        try {
            val result = blockingStub.putJobSync(ODUtils.genImageRequest(id, data))
            println("RPC putJobSync success")
            return result

        } catch (e: StatusRuntimeException) {
            println("RPC failed: " + e.status)
        }
        return null
    }

    fun sayHello() {
        try {
            println("will ping")
            blockingStub.sayHello(ODUtils.genRemoteClient(ODClient()))
            println("pinged")
        }catch (e: StatusRuntimeException){
            println("Error pinging")
        }
    }

    fun getModels() : Set<ODModel> {
        val result : ODProto.Models
        try {
            result = blockingStub.listModels(Empty.getDefaultInstance())
        }catch (e: StatusRuntimeException) {
            println("RPC Failed: " + e.status)
            return emptySet()
        }
        return ODUtils.parseModels(result)
    }

    fun ping() {
        try {
            //ODProto.Empty.newBuilder().build()
            println("will ping")
            blockingStub.ping(Empty.newBuilder().build())
            println("pinged")
        }catch (e: StatusRuntimeException){
            println("Error pinging")
        }
    }
}