package pt.up.fc.dcc.hyrax.odlib.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.ODCommunicationGrpc
import pt.up.fc.dcc.hyrax.odlib.ODLib
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Server that manages startup/shutdown of a `Greeter` server.
 *
 * Note: this file was automatically converted from Java
 */
class grpcServer(private val port: Int = 50051) {

    private var server: Server? = null

    @Throws(IOException::class)
    private fun start() {
        server = ServerBuilder.forPort(port)
                .addService(ODCommunicationImpl())
                .build()
                .start()
        logger.log(Level.INFO, "Server started, listening on {0}", port)
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down")
                this@grpcServer.stop()
                System.err.println("*** server shut down")
            }
        })
    }

    private fun stop() {
        server?.shutdown()
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    @Throws(InterruptedException::class)
    private fun blockUntilShutdown() {
        server?.awaitTermination()
    }

    internal class ODCommunicationImpl : ODCommunicationGrpc.ODCommunicationImplBase() {

        override fun putJobAsync(req: ODLib.Image?, responseObserver: StreamObserver<ODLib.Status>) {
            val reply = ODLib.Status.newBuilder().setCode(0).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }

        override fun putResultAsync(request: ODLib.Results?, responseObserver: StreamObserver<ODLib.Status>) {
            val reply = ODLib.Status.newBuilder().setCode(0).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }

        override fun putJobSync(request: ODLib.Image?, responseObserver: StreamObserver<ODLib.Results>) {
            val reply = ODLib.Results.newBuilder().build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }

    }

    companion object {
        private val logger = Logger.getLogger(grpcServer::class.java.name)

        @Throws(IOException::class, InterruptedException::class)
        //@JvmStatic
        fun startServer() {
            val server = grpcServer()
            server.start()
            server.blockUntilShutdown()
        }
    }
}
