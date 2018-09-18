package pt.up.fc.dcc.hyrax.odlib.grpc

import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import pt.up.fc.dcc.hyrax.odlib.ODModel
import pt.up.fc.dcc.hyrax.odlib.protoc.ODCommunicationGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import java.util.concurrent.TimeUnit


class GRPCClient
    /** Construct client for accessing RouteGuide server using the existing channel.  */
internal constructor(private val channel: ManagedChannel) {
    //private val logger = Logger.getLogger(GRPCClient::class.java.name)
    private val blockingStub: ODCommunicationGrpc.ODCommunicationBlockingStub = ODCommunicationGrpc.newBlockingStub(channel)


    /** Construct client connecting to HelloWorld server at `host:port`.  */
    constructor(host: String, port: Int) : this(ManagedChannelBuilder.forAddress(host, port)
    // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
    // needing certificates.
    .usePlaintext()
            .build())


    @Throws(InterruptedException::class)
    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }


    /** Say hello to server.  */
    fun putJobAsync(id: Int, data: ByteArray) {
        //logger.log(Level.INFO, "Will try to greet {0}...", name)
        val request = ODProto.Image.newBuilder().setId(id).setData(ByteString.copyFrom(data)).build()
        try {
            blockingStub.putJobAsync(request)
        } catch (e: StatusRuntimeException) {
            println("RPC failed: " + e.status)
            return
        }
        println("RPC putJobAsync success")
    }

    fun getModels() : HashSet<ODModel> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    /*companion object {
        private val logger = Logger.getLogger(GRPCClient::class.java.name)

        /**
         * Greet server. If provided, the first element of `args` is the name to use in the
         * greeting.
         */
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val client = GRPCClient("localhost", 50051)
            try {
                /* Access a service running on the local machine on port 50051 */
                val user = if (args.size > 0) "world" else "world"
                client.putJobAsync(user)
            } finally {
                client.shutdown()
            }
        }
    }*/
}