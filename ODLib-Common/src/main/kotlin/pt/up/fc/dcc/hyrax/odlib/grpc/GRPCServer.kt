package pt.up.fc.dcc.hyrax.odlib.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.protoc.ODCommunicationGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.ODService
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback
import java.io.IOException

/**
 * Server that manages startup/shutdown of a `Greeter` server.
 *
 * Note: this file was automatically converted from Java
 */
internal class GRPCServer(private val port: Int = 50051, internal val odService: ODService) {

    private var server: Server? = null

    @Throws(IOException::class)
    fun start() : GRPCServer{
        server = ServerBuilder.forPort(port)
                .addService(ODCommunicationImpl())
                .build()
                .start()
        //logger.log(Level.INFO, "Server started, listening on {0}", port)
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down")
                this@GRPCServer.stop()
                System.err.println("*** server shut down")
            }
        })
        return this
    }

    fun stop() {
        server?.shutdown()
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    @Throws(InterruptedException::class)
    private fun blockUntilShutdown() {
        server?.awaitTermination()
    }

    /*internal fun startServer() {
        val server = GRPCServer()
        server.start()
        server.blockUntilShutdown()
    }*/

    inner class ODCommunicationImpl : ODCommunicationGrpc.ODCommunicationImplBase() {

        // Just send to odService and return
        override fun putJobAsync(req: ODProto.Image?, responseObserver: StreamObserver<ODProto.Status>) {
            val reply = ODProto.Status.newBuilder().setCode(odService.putJob().code).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }

        // Just send to odService and return
        override fun putResultAsync(request: ODProto.Results?, responseObserver: StreamObserver<ODProto.Status>) {
            val reply = ODProto.Status.newBuilder().setCode(odService.putJob().code).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }

        // Wait for an answer and send it back
        override fun putJobSync(request: ODProto.Image?, responseObserver: StreamObserver<ODProto.Results>) {
            class xpto : ODCallback {
                override fun onNewResult() {
                    val reply = ODProto.Results.newBuilder().build()
                    responseObserver.onNext(reply)
                    responseObserver.onCompleted()
                }
            }
            odService.putJobAndWay(xpto())
        }
    }
}
